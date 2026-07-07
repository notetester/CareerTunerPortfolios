package com.careertuner.analysis.ai.provider;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;

/**
 * C 담당 적합도/취업분석 AI provider 선택 + 자체모델(OSS) 설정.
 *
 * <p>{@code provider=openai}(기본) 면 기존 {@link CareerAnalysisOpenAiClient} 경로를 그대로 쓰고,
 * {@code provider=oss} + {@code oss.base-url} 설정 시 자체 파인튜닝 모델
 * (Ollama OpenAI 호환 {@code /v1/chat/completions}, {@code careertuner-c-career-strategy-3b})을 1차로 시도한다.
 *
 * <p>원격 호출 경로(공유 4090 Ollama 접근: Tailscale/LAN/로컬)는 미확정이므로 base-url 을 하드코딩하지 않고
 * env 로 주입한다(기본값 비움 → 미설정 시 OSS 비활성, 기존 동작 유지).
 *
 * <p>max-tokens 는 최소 1024(권장 1280~1536). C 설명 출력은 길어서 그 미만이면 JSON 이 잘려 파싱 실패한다
 * (4090 검증: max-new 512 truncation → 1024 통과).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.analysis.ai")
public class CareerAnalysisAiProviderProperties {

    private static final Logger log = LoggerFactory.getLogger(CareerAnalysisAiProviderProperties.class);

    /** 설명 JSON truncation 방지 하한. 이 미만이면 자체모델 응답이 잘려 파싱이 깨진다(4090 검증: 512 실패 → 1024 통과). */
    private static final int MIN_SAFE_MAX_TOKENS = 1024;

    /** openai(기본/폴백) | oss(자체 파인튜닝 모델) */
    private String provider = "openai";

    private Oss oss = new Oss();

    /**
     * 폴백 체인 전체(OSS→Claude→OpenAI)의 총 시간예산. 이제 <b>각 tier 의 첫 시도는 절대 못 자르는
     * (per-tier 타임아웃 우선), 재시도 증폭만 억제하는 보조 상한</b>이다. 각 tier 의 첫 시도는
     * {@link #claudeTimeout}/{@link #openaiTimeout} 이 보장하고, 이 값은 클라이언트 내부 재시도가
     * 남은 예산을 넘기지 않게만 막는다. 기본 120s, <b>0/음수 = 무제한(재시도만 각 클라이언트
     * MAX_ATTEMPTS 까지)</b>.
     */
    private Duration chainTotalTimeBudget = Duration.ofSeconds(120);

    /**
     * Claude tier 의 "최소 보장" per-attempt 타임아웃 — 체인 total-time-budget 이 소진돼도 이 tier 의
     * 첫 시도는 이 시간을 보장받는다(우선). 기본 30s.
     */
    private Duration claudeTimeout = Duration.ofSeconds(30);

    /**
     * OpenAI tier 의 "최소 보장" per-attempt 타임아웃 — 체인 total-time-budget 이 소진돼도 이 tier 의
     * 첫 시도는 이 시간을 보장받는다(우선). 기본 30s.
     */
    private Duration openaiTimeout = Duration.ofSeconds(30);

    public boolean isOss() {
        return "oss".equalsIgnoreCase(provider);
    }

    /** 부팅 시 명백한 오설정을 빨리 잡는다(시연 중 cryptic 파싱오류 방지). */
    @PostConstruct
    void validate() {
        if (oss.getMaxTokens() < MIN_SAFE_MAX_TOKENS) {
            throw new IllegalStateException(
                    "careertuner.analysis.ai.oss.max-tokens 는 " + MIN_SAFE_MAX_TOKENS + " 이상이어야 합니다 (현재 "
                            + oss.getMaxTokens() + "). 이 미만은 설명 JSON 이 잘려 자체모델 응답이 깨집니다.");
        }
        if (isOss() && !oss.configured()) {
            log.warn("provider=oss 이지만 oss.base-url 이 비어 있어 자체모델이 비활성입니다 — OpenAI/Mock 으로 동작합니다. "
                    + "자체모델을 쓰려면 CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL 을 설정하세요.");
        }
    }

    @Getter
    @Setter
    public static class Oss {
        /** Ollama OpenAI 호환 엔드포인트 (예: http://<4090>:11434/v1). 비어 있으면 OSS 비활성. */
        private String baseUrl = "";
        private String model = "careertuner-c-career-strategy-3b";
        /** 자체 서버 인증 키(필요 시. Ollama 는 보통 불필요). */
        private String apiKey = "";
        /** 출력 토큰 상한. 설명이 길어서 1024 미만이면 JSON truncation 위험 → 최소 1024 권장. */
        private int maxTokens = 1280;
        private double temperature = 0.2;
        private Duration timeout = Duration.ofSeconds(60);
        /** 일시적 실패(5xx/네트워크/JSON 깨짐) 재시도 횟수. 소형 모델 JSON 안정성 보강(총 시도 = maxRetries+1). */
        private int maxRetries = 2;
        /** 재시도 간 백오프 기준(선형 증가: 1·2·3배). */
        private Duration retryBackoff = Duration.ofMillis(400);
        /**
         * 한 번의 설명 생성 호출(재시도·백오프 포함)의 총 시간예산. E(CorrectionAiClient)의 totalTimeBudget
         * 패턴 미러 — 공유 4090에 다모델 동시 부하 시 재시도가 GPU 점유를 무한정 끌지 않게 상한을 둔다.
         * (기존: 요청당 60초 × 최대 3시도 + 백오프 ≈ 181초까지 가능했음 → 90초로 상한.)
         * 기본 90초, <b>0 또는 음수 = 무제한(예산 OFF, 기존 무예산 경로)</b>.
         */
        private Duration totalTimeBudget = Duration.ofSeconds(90);
        /** 설명이 '부족 역량을 보유로 서술'(grounding 위반)하면 재호출 횟수. 소진 시 폴백. */
        private int groundingRetries = 1;

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }
}
