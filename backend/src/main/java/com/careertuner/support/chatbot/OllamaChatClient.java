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

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.community.moderation.config.OllamaProperties;

/**
 * 챗봇 답변 생성용 Ollama /api/chat 클라이언트.
 * 기존 OllamaClient(검열용)를 수정하지 않고 별도 구현.
 *
 * <p>Ollama 가 죽거나 비면 {@link SupportTextFallbackGenerator} 가 Claude(Haiku)→목업으로 폴백하므로,
 * 사용자가 챗봇에 질문했을 때 어떤 상황에서도 화면이 깨지지 않는다.
 */
@Component
public class OllamaChatClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatClient.class);

    private final RestClient restClient;
    private final ChatbotProperties chatbotProps;
    private final SupportTextFallbackGenerator fallback;
    private final GpuPermitGate gpuPermitGate;
    private final String systemPrompt;

    public OllamaChatClient(OllamaProperties ollamaProps, ChatbotProperties chatbotProps,
                            SupportTextFallbackGenerator fallback, GpuPermitGate gpuPermitGate) {
        this.chatbotProps = chatbotProps;
        this.fallback = fallback;
        this.gpuPermitGate = gpuPermitGate;

        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        // 예산 ON 이면 read timeout 을 예산으로 절삭(단일 시도 대비)
        requestFactory.setReadTimeout(capReadTimeout(Duration.ofSeconds(60), ollamaProps.getTotalTimeBudget()));

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
        return fallback.generate(prompt, userQuestion,
                () -> callOllama(prompt, userQuestion),
                "현재 챗봇 답변을 생성할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    private String callOllama(String prompt, String userQuestion) {
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

        Map<String, Object> response;
        try (GpuPermitGate.GpuPermit permit = gpuPermitGate.acquire("chatbot")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ollamaResponse = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            response = ollamaResponse;
        }

        if (response == null || !response.containsKey("message")) {
            throw new IllegalStateException("Ollama chat 응답이 비어 있습니다");
        }

        Object messageObj = response.get("message");
        if (!(messageObj instanceof Map)) {
            throw new IllegalStateException("Ollama chat 응답의 message 형식이 올바르지 않습니다");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) messageObj;
        Object content = message.get("content");
        return content == null ? "" : content.toString().strip();
    }

    /** 총 시간예산이 양수(ON)면 read timeout 을 예산 이하로 절삭한다. 0/음수/null 은 무제한(OFF, 기존 동작). */
    private static Duration capReadTimeout(Duration readTimeout, Duration totalTimeBudget) {
        if (totalTimeBudget == null || totalTimeBudget.isZero() || totalTimeBudget.isNegative()) {
            return readTimeout;
        }
        return readTimeout.compareTo(totalTimeBudget) <= 0 ? readTimeout : totalTimeBudget;
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
