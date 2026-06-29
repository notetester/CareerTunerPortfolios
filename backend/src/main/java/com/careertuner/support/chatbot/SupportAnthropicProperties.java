package com.careertuner.support.chatbot;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 운영/지원 텍스트 AI(FAQ·티켓 초안, 지원 챗봇)의 Claude(Haiku) 설정 — Ollama 다음 단계 폴백 provider.
 *
 * <p>공통 {@code careertuner.anthropic} 키(ANTHROPIC_API_KEY/MODEL)를 도메인 독립적으로 다시 바인딩한다.
 * 다른 도메인 설정과 같은 prefix 를 공유하되, 패키지 의존을 만들지 않기 위해 support 패키지 안에 둔다.
 * 키가 비어 있으면 폴백 디스패처가 이 단계를 건너뛰고 목업으로 넘어간다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.anthropic")
public class SupportAnthropicProperties {

    private String apiKey = "";
    private String model = "claude-haiku-4-5-20251001";
    private String baseUrl = "https://api.anthropic.com/v1";
    /** Anthropic 필수 헤더 anthropic-version. */
    private String version = "2023-06-01";
    /** Anthropic 은 max_tokens 가 필수. 초안/답변은 길지 않아 넉넉히 잡는다. */
    private int maxTokens = 2048;
    private Duration timeout = Duration.ofSeconds(60);

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
