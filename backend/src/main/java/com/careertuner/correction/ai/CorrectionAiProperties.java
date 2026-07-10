package com.careertuner.correction.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.correction.ai")
public class CorrectionAiProperties {

    private String provider = "openai";
    private boolean fallbackEnabled = true;
    private Duration openAiTimeout = Duration.ofSeconds(30);
    private int openAiMaxAttempts = 1;
    private Self self = new Self();
    private Warmup warmup = new Warmup();

    public boolean selfProviderEnabled() {
        return "self".equalsIgnoreCase(provider) && self.configured();
    }

    @Getter
    @Setter
    public static class Self {
        private String baseUrl = "";
        private String model = "careertuner-e-correction-3b:delivery-s-f16-20260708";
        private String apiKey = "";
        private int maxTokens = 3072;
        private double temperature = 0.0;
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration timeout = Duration.ofSeconds(20);
        /** 재시도·백오프를 포함한 자체 LLM 체인 총 시간예산. 기본 30초, 0 또는 음수 = 무제한(예산 OFF). */
        private Duration totalTimeBudget = Duration.ofSeconds(30);
        private int maxAttempts = 2;
        private Duration retryBackoff = Duration.ofMillis(400);

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Warmup {
        private boolean enabled = true;
        private Duration keepAlive = Duration.ofMinutes(10);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private Duration retryCooldown = Duration.ofMinutes(1);
    }
}
