package com.careertuner.analysis.ai.provider;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * analysis 계열(적합도·트렌드 등) AI 의 Claude(Haiku) 설정. OpenAI 앞단의 1차 폴백 provider.
 *
 * <p>공통 {@code careertuner.anthropic} 키(ANTHROPIC_API_KEY/MODEL)를 도메인 독립적으로 다시 바인딩한다.
 * D 의 면접 도메인 설정({@code interview.service.AnthropicProperties})과 같은 prefix 를 공유하지만,
 * 패키지 의존을 만들지 않기 위해 C 소유 패키지 안에 별도 어댑터로 둔다(C 의 "각자 어댑터" 가이드).
 * 키가 비어 있으면 폴백 디스패처가 이 단계를 건너뛰고 OpenAI 로 넘어간다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.anthropic")
public class CareerAnalysisAnthropicProperties {

    private String apiKey = "";
    private String model = "claude-haiku-4-5-20251001";
    private String baseUrl = "https://api.anthropic.com/v1";
    /** Anthropic 필수 헤더 anthropic-version. */
    private String version = "2023-06-01";
    /** Anthropic 은 max_tokens 가 필수. 적합도 분석은 출력이 길어 넉넉히 잡는다. */
    private int maxTokens = 8192;
    private Duration timeout = Duration.ofSeconds(120);

    public String messagesUrl() {
        return baseUrl.replaceAll("/+$", "") + "/messages";
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
