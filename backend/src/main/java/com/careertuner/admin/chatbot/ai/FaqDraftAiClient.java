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

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.community.moderation.config.OllamaProperties;
import com.careertuner.support.chatbot.SupportTextFallbackGenerator;

/**
 * 운영 패널 2단계 — 답 못한 질문으로 FAQ 답변 초안을 생성하는 Ollama /api/chat 클라이언트.
 * TicketDraftAiClient(#34) 와 동일한 동기 호출 패턴을 따르되, FAQ 초안 전용 프롬프트를 쓴다.
 *
 * <p>Ollama 가 죽거나 비면 {@link SupportTextFallbackGenerator} 가 Claude(Haiku)→목업으로 폴백하므로,
 * 운영자가 "초안 생성"을 눌렀을 때 어떤 상황에서도 화면이 깨지지 않는다.
 */
@Component
public class FaqDraftAiClient {

    private static final Logger log = LoggerFactory.getLogger(FaqDraftAiClient.class);

    private final RestClient restClient;
    private final OllamaProperties ollamaProps;
    private final SupportTextFallbackGenerator fallback;
    private final GpuPermitGate gpuPermitGate;
    private final String systemPrompt;

    public FaqDraftAiClient(OllamaProperties ollamaProps, SupportTextFallbackGenerator fallback,
                            GpuPermitGate gpuPermitGate) {
        this.ollamaProps = ollamaProps;
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

    /** 질문(+참고 FAQ) 컨텍스트를 받아 FAQ 답변 초안 본문을 생성한다(Ollama→Claude→목업 폴백). */
    public String generateDraft(String faqContext) {
        String prompt = systemPrompt.replace("{faqContext}", faqContext);
        return fallback.generate(prompt, faqContext,
                () -> callOllama(prompt, faqContext),
                "현재 AI FAQ 초안을 생성할 수 없습니다. 잠시 후 다시 시도하거나 직접 작성해 주세요.");
    }

    private String callOllama(String prompt, String faqContext) {
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

        Map<String, Object> response;
        try (GpuPermitGate.GpuPermit permit = gpuPermitGate.acquire("admin-faq")) {
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
