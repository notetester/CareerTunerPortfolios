package com.careertuner.analysis.ai.provider;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    /** openai(기본/폴백) | oss(자체 파인튜닝 모델) */
    private String provider = "openai";

    private Oss oss = new Oss();

    public boolean isOss() {
        return "oss".equalsIgnoreCase(provider);
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

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }
}
