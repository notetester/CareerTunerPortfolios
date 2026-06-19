package com.careertuner.support.chatbot;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.careertuner.community.moderation.config.OllamaProperties;

/**
 * 챗봇 답변 생성용 Ollama /api/chat 클라이언트.
 * 기존 OllamaClient(검열용)를 수정하지 않고 별도 구현.
 */
@Component
public class OllamaChatClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatClient.class);

    private final RestClient restClient;
    private final ChatbotProperties chatbotProps;
    private final String systemPrompt;

    public OllamaChatClient(OllamaProperties ollamaProps, ChatbotProperties chatbotProps) {
        this.chatbotProps = chatbotProps;

        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(ollamaProps.getBaseUrl())
                .requestFactory(requestFactory)
                .build();

        this.systemPrompt = loadSystemPrompt();
    }

    public String generateAnswer(String faqContext, String userQuestion) {
        String prompt = systemPrompt
                .replace("{faqContext}", faqContext)
                .replace("{userQuestion}", userQuestion);

        Map<String, Object> request = Map.of(
                "model", chatbotProps.getChatModel(),
                "stream", false,
                "options", Map.of("temperature", 0.3, "num_ctx", 4096),
                "messages", List.of(
                        Map.of("role", "system", "content", prompt),
                        Map.of("role", "user", "content", userQuestion)
                )
        );

        log.debug("챗봇 답변 생성 요청: model={}", chatbotProps.getChatModel());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("message")) {
            throw new IllegalStateException("Ollama chat 응답이 비어 있습니다");
        }

        @SuppressWarnings("unchecked")
        Map<String, String> message = (Map<String, String>) response.get("message");
        return message.get("content");
    }

    private String loadSystemPrompt() {
        try (InputStream is = new ClassPathResource("prompts/chatbot-system.txt").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("시스템 프롬프트 로드 실패, 기본 프롬프트 사용", e);
            return "아래 [참고 FAQ]를 근거로 사용자 질문에 답하라. FAQ에 없는 내용은 지어내지 마라.\n\n[참고 FAQ]\n{faqContext}\n\n[사용자 질문]\n{userQuestion}";
        }
    }
}
