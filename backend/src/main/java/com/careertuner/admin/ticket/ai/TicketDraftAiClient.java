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
import com.careertuner.support.chatbot.SupportTextFallbackGenerator;

/**
 * 상담사 AI 어시스트 — 티켓 답변 초안 생성용 Ollama /api/chat 클라이언트.
 * 챗봇용 OllamaChatClient(#35)와 동일한 호출 패턴을 따르되, 상담 답변 초안 프롬프트를 사용한다.
 *
 * <p>Ollama 가 죽거나 비면 {@link SupportTextFallbackGenerator} 가 Claude(Haiku)→목업으로 폴백하므로,
 * 상담사가 "초안 생성"을 눌렀을 때 어떤 상황에서도 화면이 깨지지 않는다.
 */
@Component
public class TicketDraftAiClient {

    private static final Logger log = LoggerFactory.getLogger(TicketDraftAiClient.class);

    private final RestClient restClient;
    private final OllamaProperties ollamaProps;
    private final SupportTextFallbackGenerator fallback;
    private final String systemPrompt;

    public TicketDraftAiClient(OllamaProperties ollamaProps, SupportTextFallbackGenerator fallback) {
        this.ollamaProps = ollamaProps;
        this.fallback = fallback;

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

    /** 문의 스레드 컨텍스트를 받아 상담사용 답변 초안을 생성한다(Ollama→Claude→목업 폴백). */
    public String generateDraft(String ticketContext) {
        String prompt = systemPrompt.replace("{ticketContext}", ticketContext);
        return fallback.generate(prompt, ticketContext,
                () -> callOllama(prompt, ticketContext),
                "현재 AI 답변 초안을 생성할 수 없습니다. 잠시 후 다시 시도하거나 직접 작성해 주세요.");
    }

    private String callOllama(String prompt, String ticketContext) {
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

        Object messageObj = response.get("message");
        if (!(messageObj instanceof Map)) {
            throw new IllegalStateException("Ollama chat 응답의 message 형식이 올바르지 않습니다");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) messageObj;
        Object content = message.get("content");
        return content == null ? "" : content.toString().strip();
    }

    private static final String SUMMARY_SYSTEM_PROMPT =
            "너는 CareerTuner 고객센터 상담사를 돕는 AI 어시스턴트다. "
          + "아래 회원 정보와 과거 문의 이력을 읽고, 상담사가 이 회원을 빠르게 파악하도록 "
          + "3~4문장의 한국어 존댓말 요약을 작성하라. 가입 시기·구독 등급·과거 문의 빈도와 해결 양상·이번 문의 맥락을 "
          + "자연스럽게 엮되, 제공된 정보에 없는 사실(결제 금액·외부 데이터 등)은 절대 지어내지 마라. "
          + "비밀번호·연락처 같은 민감정보는 언급하지 마라. 요약 본문만 출력한다.";

    /**
     * 회원 정보·과거 문의 이력 컨텍스트를 받아 상담사용 회원 요약을 생성한다.
     * 초안 생성과 동일한 Ollama /api/chat 호출 인프라를 재사용하되 요약 프롬프트를 쓴다.
     */
    public String summarizeMember(String memberContext) {
        Map<String, Object> request = Map.of(
                "model", ollamaProps.getModel(),
                "stream", false,
                "options", Map.of("temperature", 0.3, "num_ctx", 4096),
                "messages", List.of(
                        Map.of("role", "system", "content", SUMMARY_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", memberContext)
                )
        );

        log.debug("회원 요약 생성 요청: model={}", ollamaProps.getModel());

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
            log.error("회원 요약 생성 실패", e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
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
