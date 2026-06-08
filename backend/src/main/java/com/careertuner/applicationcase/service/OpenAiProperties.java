package com.careertuner.applicationcase.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.openai")
public class OpenAiProperties {

    private String apiKey = "";
    private String model = "gpt-5";
    private String baseUrl = "https://api.openai.com/v1";
    private Duration timeout = Duration.ofSeconds(90);

    public String responsesUrl() {
        return baseUrl.replaceAll("/+$", "") + "/responses";
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
