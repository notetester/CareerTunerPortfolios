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
