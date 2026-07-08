package com.careertuner.applicationcase.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.b-analysis")
public class BAnalysisProperties {

    private LocalLlm localLlm = new LocalLlm();
    private Company company = new Company();

    /**
     * 기업분석 provider 정책. 공고분석(job)은 항상 현행 폴백 체인을 유지하고, 기업분석(company)만
     * 이 설정으로 1순위 provider 를 바꾼다(R1 결과 품질 이슈 대응 — hosted 우선 실험용).
     */
    @Getter
    @Setter
    public static class Company {

        /**
         * 기업분석 1순위 provider. 값(대소문자 무시):
         * <ul>
         * <li>{@code auto}(기본) — 현행과 동일: 자체 R1 → Claude → OpenAI → self-rules.</li>
         * <li>{@code openai} — OpenAI → Claude → R1 → self-rules.</li>
         * <li>{@code claude} — Claude → OpenAI → R1 → self-rules.</li>
         * </ul>
         * 어느 경우든 활성/미설정 provider 는 건너뛰고, 최종 안전망은 항상 self-rules-v1 이다.
         */
        private String provider = "auto";

        /**
         * 기업분석 OpenAI 경로에서만 사용할 모델 override. 비어 있으면(기본) 공용 {@code careertuner.openai.model}
         * 을 그대로 쓴다. 값이 있으면 기업분석 OpenAI 호출만 이 모델로 바꾸고, 공고분석 폴백·메타데이터 추출·
         * 이미지/PDF OCR 등 다른 OpenAI 호출은 공용 모델을 유지한다(스코프 격리).
         * (예: 지연·비용 최적화용 {@code gpt-5.4-mini}.)
         */
        private String openAiModel = "";
    }

    @Getter
    @Setter
    public static class LocalLlm {

        private boolean enabled = false;
        private String baseUrl = "http://localhost:11434";
        private String model = "careertuner-b-jobposting-r1";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(480);
        private int numPredict = 2048;
        // Ollama 컨텍스트 윈도(입력+출력 토큰 합). 긴 공고가 이 값을 넘기면 Ollama가 400(exceed_context_size)을
        // 던져 R1 경로가 통째로 self-rules로 폴백된다(이슈 D 검증: 동국제약 9901토큰>8192). 기본값은 기존 동작 유지.
        private int numCtx = 8192;
        private int maxRetries = 1;
        private double groundingThreshold = 0.6;
        /**
         * 로컬 LLM <b>시도 1건</b>의 read timeout 상한(총 시간예산). 0 또는 음수 = 무제한(OFF).
         * 재시도(maxRetries)는 서비스 계층에서 수행되므로 총 상한 = (maxRetries+1) x 이 값.
         *
         * <p><b>기본 180s</b>(이전 0=무제한이라 폴백 체인 최악 대기가 ~34분까지 가능했음 → 유계화).
         * readTimeout(480s)은 긴 공고용 상한이므로 그대로 두고, 이 값이 실제 per-attempt 대기를
         * min(readTimeout, budget)=180s 로 묶는다. 매우 긴 공고 분석이 필요하면 담당자가 상향한다.
         */
        private Duration totalTimeBudget = Duration.ofSeconds(180);

        public String chatUrl() {
            return baseUrl.replaceAll("/+$", "") + "/api/chat";
        }
    }
}
