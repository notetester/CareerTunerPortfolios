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
        private int maxRetries = 1;
        private double groundingThreshold = 0.6;

        public String chatUrl() {
            return baseUrl.replaceAll("/+$", "") + "/api/chat";
        }
    }
}
