package com.careertuner.correction.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.anthropic")
public class CorrectionAnthropicProperties {

    private String apiKey = "";
    private String model = "claude-haiku-4-5-20251001";
    private String baseUrl = "https://api.anthropic.com/v1";
    private String version = "2023-06-01";
    private Duration timeout = Duration.ofSeconds(45);

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String messagesUrl() {
        return baseUrl.replaceAll("/+$", "") + "/messages";
    }
}
