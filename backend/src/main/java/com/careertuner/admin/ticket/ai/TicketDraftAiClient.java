package com.careertuner.admin.ticket.ai;

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
 * 상담사 AI 어시스트 — 티켓 답변 초안 생성용 Ollama /api/chat 클라이언트.
 * 챗봇용 OllamaChatClient(#35)와 동일한 호출 패턴을 따르되, 상담 답변 초안 프롬프트를 사용한다.
 * 상담사가 "초안 생성"을 눌렀을 때 즉시 응답해야 하므로 비동기 리스너가 아닌 동기 호출이다.
 */
@Component
public class TicketDraftAiClient {

    private static final Logger log = LoggerFactory.getLogger(TicketDraftAiClient.class);

    private final RestClient restClient;
    private final OllamaProperties ollamaProps;
    private final String systemPrompt;

    public TicketDraftAiClient(OllamaProperties ollamaProps) {
        this.ollamaProps = ollamaProps;

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

    /** 문의 스레드 컨텍스트를 받아 상담사용 답변 초안을 생성한다. */
    public String generateDraft(String ticketContext) {
        String prompt = systemPrompt.replace("{ticketContext}", ticketContext);

        Map<String, Object> request = Map.of(
                "model", ollamaProps.getModel(),
                "stream", false,
                "options", Map.of("temperature", 0.4, "num_ctx", 4096),
                "messages", List.of(
                        Map.of("role", "system", "content", prompt),
                        Map.of("role", "user", "content", ticketContext)
                )
        );

        log.debug("티켓 답변 초안 생성 요청: model={}", ollamaProps.getModel());

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
        String content = message.get("content");
        return content == null ? "" : content.strip();
    }

    private String loadSystemPrompt() {
        try (InputStream is = new ClassPathResource("prompts/ticket-draft-system.txt").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("티켓 초안 프롬프트 로드 실패, 기본 프롬프트 사용", e);
            return "너는 CareerTuner 고객센터 상담사를 돕는 AI 어시스턴트다. "
                    + "아래 문의 내용을 읽고 고객에게 보낼 정중한 답변 초안을 한국어 존댓말로 작성하라. "
                    + "확인되지 않은 사실은 단정하지 마라.\n\n[문의 내용]\n{ticketContext}";
        }
    }
}
