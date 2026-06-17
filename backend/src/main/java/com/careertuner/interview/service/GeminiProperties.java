package com.careertuner.interview.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 면접 도메인 Gemini 설정. 면접 LLM 호출의 1차(기본) provider.
 *
 * <p>키가 비어 있으면 호출 자체를 시도하지 않고 자동으로 OpenAI 로 폴백한다
 * ({@link FallbackInterviewLlmGateway}). 합성 데이터 증류용 선생 모델로 Flash-Lite 를 기본값으로 둔다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.gemini")
public class GeminiProperties {

    private String apiKey = "";
    private String model = "gemini-2.5-flash-lite";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private Duration timeout = Duration.ofSeconds(120);

    public String generateContentUrl() {
        return baseUrl.replaceAll("/+$", "") + "/models/" + model + ":generateContent";
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
