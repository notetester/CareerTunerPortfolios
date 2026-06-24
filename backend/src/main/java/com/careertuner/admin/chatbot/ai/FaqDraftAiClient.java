package com.careertuner.admin.chatbot.ai;

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
import org.springframework.web.client.RestClientException;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.moderation.config.OllamaProperties;

/**
 * 운영 패널 2단계 — 답 못한 질문으로 FAQ 답변 초안을 생성하는 Ollama /api/chat 클라이언트.
 * TicketDraftAiClient(#34) 와 동일한 동기 호출 패턴을 따르되, FAQ 초안 전용 프롬프트를 쓴다.
 * 운영자가 "초안 생성"을 눌렀을 때 즉시 응답해야 하므로 동기 호출이다.
 */
@Component
public class FaqDraftAiClient {

    private static final Logger log = LoggerFactory.getLogger(FaqDraftAiClient.class);

    private final RestClient restClient;
    private final OllamaProperties ollamaProps;
    private final String systemPrompt;

    public FaqDraftAiClient(OllamaProperties ollamaProps) {
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

    /** 질문(+참고 FAQ) 컨텍스트를 받아 FAQ 답변 초안 본문을 생성한다. */
    public String generateDraft(String faqContext) {
        String prompt = systemPrompt.replace("{faqContext}", faqContext);

        Map<String, Object> request = Map.of(
                "model", ollamaProps.getModel(),
                "stream", false,
                "options", Map.of("temperature", 0.4, "num_ctx", 4096),
                "messages", List.of(
                        Map.of("role", "system", "content", prompt),
                        Map.of("role", "user", "content", faqContext)
                )
        );

        log.debug("FAQ 초안 생성 요청: model={}", ollamaProps.getModel());

        try {
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

            Object messageObj = response.get("message");
            if (!(messageObj instanceof Map)) {
                throw new IllegalStateException("Ollama chat 응답의 message 형식이 올바르지 않습니다");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) messageObj;
            Object content = message.get("content");
            return content == null ? "" : content.toString().strip();
        } catch (RestClientException | ClassCastException e) {
            log.error("FAQ 초안 생성 실패", e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private String loadSystemPrompt() {
        try (InputStream is = new ClassPathResource("prompts/faq-draft-system.txt").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("FAQ 초안 프롬프트 로드 실패, 기본 프롬프트 사용", e);
            return "너는 CareerTuner 고객센터의 FAQ를 작성하는 AI 어시스턴트다. "
                    + "아래 질문을 읽고 누구에게나 통하는 FAQ 답변 초안을 한국어 존댓말로, 본문만 작성하라. "
                    + "확인되지 않은 사실은 단정하지 마라.\n\n{faqContext}";
        }
    }
}
