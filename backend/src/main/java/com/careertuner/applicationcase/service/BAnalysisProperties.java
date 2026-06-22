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
        private String model = "qwen2.5:3b-instruct";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(30);

        public String chatUrl() {
            return baseUrl.replaceAll("/+$", "") + "/api/chat";
        }
    }
}
