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
    /**
     * true 면 워커에 파일 바이트(base64)를 함께 전송해 파일경로 공유(co-location) 없이 OCR 할 수 있다.
     * 기본 off — 기존 filePath 방식 그대로. servlet multipart/업로드 실효 한도(≤20MB) 안에서만 사용한다.
     */
    private boolean sendBytes = false;
    private String baseUrl = "http://127.0.0.1:8091";
    private Duration timeout = Duration.ofSeconds(30);

    public String extractJobPostingUrl() {
        return baseUrl.replaceAll("/+$", "") + "/extract/job-posting";
    }

    public String capabilitiesUrl() {
        return baseUrl.replaceAll("/+$", "") + "/capabilities";
    }
}
