package com.careertuner.jobposting.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.extraction.ai-worker")
public class JobPostingAiWorkerProperties {

    private boolean enabled = false;
    private String baseUrl = "http://127.0.0.1:8091";
    private Duration timeout = Duration.ofSeconds(30);

    public String extractJobPostingUrl() {
        return baseUrl.replaceAll("/+$", "") + "/extract/job-posting";
    }
}
