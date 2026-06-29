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

    public boolean selfProviderEnabled() {
        return "self".equalsIgnoreCase(provider) && self.configured();
    }

    @Getter
    @Setter
    public static class Self {
        private String baseUrl = "";
        private String model = "careertuner-e-correction:8b";
        private String fallbackModel = "careertuner-e-correction-3b:latest";
        private String apiKey = "";
        private int maxTokens = 1600;
        private double temperature = 0.2;
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration timeout = Duration.ofSeconds(20);
        private Duration totalTimeBudget = Duration.ofSeconds(30);
        private int primaryMaxAttempts = 2;
        private int fallbackMaxAttempts = 1;
        private Duration retryBackoff = Duration.ofMillis(400);

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }
}
