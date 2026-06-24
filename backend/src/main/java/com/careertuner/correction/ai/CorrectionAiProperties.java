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
    private Self self = new Self();

    public boolean selfProviderEnabled() {
        return "self".equalsIgnoreCase(provider) && self.configured();
    }

    @Getter
    @Setter
    public static class Self {
        private String baseUrl = "";
        private String model = "careertuner-e-correction";
        private String apiKey = "";
        private int maxTokens = 1600;
        private double temperature = 0.2;
        private Duration timeout = Duration.ofSeconds(60);
        private int maxRetries = 2;
        private Duration retryBackoff = Duration.ofMillis(400);

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }
}
