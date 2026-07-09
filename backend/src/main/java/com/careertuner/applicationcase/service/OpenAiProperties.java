package com.careertuner.applicationcase.service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

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
    private Duration timeout = Duration.ofSeconds(300);
    private boolean jobPostingFallbackEnabled = false;
    private Set<String> jobPostingFallbackAllowlist = new HashSet<>();

    public String responsesUrl() {
        return baseUrl.replaceAll("/+$", "") + "/responses";
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean jobPostingFallbackAllowed(String stage) {
        return jobPostingFallbackEnabled
                && stage != null
                && jobPostingFallbackAllowlist != null
                && jobPostingFallbackAllowlist.contains(stage);
    }
}
