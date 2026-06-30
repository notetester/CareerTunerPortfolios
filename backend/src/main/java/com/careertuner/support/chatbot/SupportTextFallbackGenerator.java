package com.careertuner.support.chatbot;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 운영/지원 텍스트 AI 의 폴백 디스패처 — primary(Ollama) → Claude(Haiku) → 목업.
 *
 * <p>FAQ 초안·티켓 초안·지원 챗봇이 공유한다. 운영툴은 OpenAI 인프라가 없으므로 OpenAI 단계는 두지 않고,
 * 공통 키로 가장 안정적인 Claude 를 1차 폴백으로, 마지막은 안내성 목업 문구로 둔다. 어떤 provider 가
 * 죽어도 예외를 던지지 않으므로(throw 금지) 운영 화면이 깨지지 않는다.
 */
@Component
public class SupportTextFallbackGenerator {

    private static final Logger log = LoggerFactory.getLogger(SupportTextFallbackGenerator.class);

    private final SupportAnthropicProperties props;
    private final RestClient anthropicClient;

    public SupportTextFallbackGenerator(SupportAnthropicProperties props) {
        this.props = props;

        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(props.getTimeout());

        this.anthropicClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * primary(Ollama) 호출을 우선 시도하고, 비거나 실패하면 Claude → 목업 순으로 폴백한다.
     *
     * @param systemPrompt 시스템 프롬프트(Claude 단계에서 재사용)
     * @param userContent  사용자/컨텍스트 입력(Claude 단계의 user 메시지)
     * @param primary      Ollama 호출 람다(예외·빈 응답이면 폴백)
     * @param mockMessage  모든 provider 실패 시 반환할 안내 문구(최종 안전망)
     */
    public String generate(String systemPrompt, String userContent,
                           Supplier<String> primary, String mockMessage) {
        // 1) primary: Ollama 자체 모델.
        try {
            String result = primary.get();
            if (result != null && !result.isBlank()) {
                return result;
            }
            log.warn("운영 AI primary(Ollama) 빈 응답 → Claude 폴백");
        } catch (RuntimeException ex) {
            log.warn("운영 AI primary(Ollama) 실패 → Claude 폴백: {}", ex.getMessage());
        }
        // 2) 1차 폴백: Claude(Haiku) — 공통 키라 가장 안정적. 키 없으면 건너뜀.
        if (props.configured()) {
            try {
                return claude(systemPrompt, userContent);
            } catch (RuntimeException ex) {
                log.warn("운영 AI Claude 실패 → 목업: {}", ex.getMessage());
            }
        }
        // 3) 최종 폴백: 목업 안내 문구 — 절대 예외로 끝나지 않는다.
        log.warn("운영 AI 모든 provider 미설정/실패 → 목업 응답");
        return mockMessage;
    }

    private String claude(String systemPrompt, String userContent) {
        Map<String, Object> request = Map.of(
                "model", props.getModel(),
                "max_tokens", props.getMaxTokens(),
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userContent)),
                "temperature", 0.4);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = anthropicClient.post()
                .uri("/messages")
                .header("x-api-key", props.getApiKey())
                .header("anthropic-version", props.getVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        String text = extractText(response);
        if (text.isBlank()) {
            throw new IllegalStateException("Anthropic 응답 본문이 비어 있습니다");
        }
        return text;
    }

    /** Anthropic Messages 응답의 content[*].text 블록을 이어붙인다. */
    private String extractText(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object content = response.get("content");
        if (!(content instanceof List<?> blocks)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Object block : blocks) {
            if (block instanceof Map<?, ?> map && "text".equals(String.valueOf(map.get("type")))) {
                Object text = map.get("text");
                if (text != null) {
                    builder.append(text);
                }
            }
        }
        return builder.toString().strip();
    }
}
