package com.careertuner.analysis.ai.provider;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * C 담당 AI 기능 전용 OpenAI 설정.
 *
 * <p>공통 AI 엔진 영역을 변경하지 않고 C 소유 도메인 안에서 실제 AI 구현을 준비하기 위해 둔다.
 * 기존 {@code careertuner.openai.*} 환경변수를 그대로 사용하므로 API 키만 주입하면 활성화된다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.openai")
public class CareerAnalysisAiProperties {

    private String apiKey = "";
    private String model = "gpt-5";
    private String baseUrl = "https://api.openai.com/v1";
    private Duration timeout = Duration.ofSeconds(90);

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String responsesUrl() {
        return baseUrl.replaceAll("/+$", "") + "/responses";
    }
}
