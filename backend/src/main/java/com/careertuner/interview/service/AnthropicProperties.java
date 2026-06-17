package com.careertuner.interview.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 면접 도메인 Anthropic(Claude) 설정. 면접 LLM 호출의 1차(기본) provider.
 *
 * <p>자체 LLM 학습의 선생(합성 데이터 증류)·런타임을 Claude 로 통일한다.
 * Gemini 무료등급이 20 RPD 로 삭감돼 대량 데이터 생성이 불가능해진 뒤 Haiku 로 선회(2026-06-17).
 * 키가 비어 있으면 호출을 시도하지 않고 자동으로 OpenAI 로 폴백한다({@link FallbackInterviewLlmGateway}).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.anthropic")
public class AnthropicProperties {

    private String apiKey = "";
    private String model = "claude-haiku-4-5-20251001";
    private String baseUrl = "https://api.anthropic.com/v1";
    /** Anthropic 필수 헤더 anthropic-version. */
    private String version = "2023-06-01";
    /** Anthropic 은 max_tokens 가 필수. 모범답안 일괄 생성 등 긴 출력을 고려해 넉넉히 잡는다. */
    private int maxTokens = 8192;
    private Duration timeout = Duration.ofSeconds(120);

    public String messagesUrl() {
        return baseUrl.replaceAll("/+$", "") + "/messages";
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
