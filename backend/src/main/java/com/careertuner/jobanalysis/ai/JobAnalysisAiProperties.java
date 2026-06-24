package com.careertuner.jobanalysis.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.job-analysis.ai")
public class JobAnalysisAiProperties {

    /** openai | oss */
    private String provider = "openai";
    /** Ollama 또는 OpenAI 호환 엔드포인트 (예: http://localhost:11434/v1). */
    private String baseUrl = "";
    /** 자체 모델 id (예: job-analysis-qwen3-4b). */
    private String model = "";
    private Duration timeout = Duration.ofSeconds(120);

    public boolean isOss() {
        return "oss".equalsIgnoreCase(provider);
    }

    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
