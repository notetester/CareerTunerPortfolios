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
    /**
     * 호출 1건의 총 시간예산. 0 또는 음수 = 무제한(OFF, 기본) — per-request timeout 만 적용.
     * 양수면 요청 타임아웃이 min(timeout, 예산)으로 절삭된다.
     */
    private Duration totalTimeBudget = Duration.ZERO;

    public boolean isOss() {
        return "oss".equalsIgnoreCase(provider);
    }

    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
