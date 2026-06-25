package com.careertuner.support.chatbot;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.careertuner.community.moderation.config.OllamaProperties;

/**
 * 화행(speech-act) 이진 분류기: 사용자 발화가 "정보를 묻는 질문(QUESTION)"인지 "작업을 시키는 명령(COMMAND)"인지.
 *
 * <p>통합 라우팅의 <b>경계구역(|intakeScore - faqScore| &lt; deadband)에서만</b> 1회 호출한다.
 * 명확구역은 임베딩 argmax 로 결정적으로 갈리므로 LLM 을 부르지 않는다(비용 제한).</p>
 *
 * <p>Phase 0 실측(2026-06-24): 경계 발화 30개를 ③과 동일 설정(qwen3:8b, temperature=0, think=false)으로
 * 5회씩 호출 → 결정성 30/30(완전일치), 정확도 29/30. 프롬프트는 system 에 규칙+2예시, user 에 발화만 넣는다
 * (한 메시지에 few-shot+"답:"을 넣으면 qwen3 가 프롬프트를 에코함 — 실측 확인).</p>
 *
 * <p>장애/모호 시 안전 기본값은 {@code QUESTION}(→ FAQ). "애매하면 FAQ 쪽" 비대칭 방어와 일관된다.</p>
 */
@Component
public class SpeechActClassifier {

    private static final Logger log = LoggerFactory.getLogger(SpeechActClassifier.class);

    public static final String QUESTION = "QUESTION";
    public static final String COMMAND = "COMMAND";

    private static final String SYSTEM_PROMPT =
            "너는 화행 분류기다. 사용자 발화가 정보를 묻으면 QUESTION, 작업을 시키면 COMMAND. "
            + "반드시 QUESTION 또는 COMMAND 한 단어만 출력한다.\n"
            + "예: 자소서 첨삭 돼요? -> QUESTION\n"
            + "예: 자소서 첨삭해줘 -> COMMAND";

    private final RestClient restClient;
    private final ChatbotProperties chatbotProps;

    public SpeechActClassifier(OllamaProperties ollamaProps, ChatbotProperties chatbotProps) {
        this.chatbotProps = chatbotProps;

        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(ollamaProps.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** 발화를 QUESTION 또는 COMMAND 로 분류. 장애/모호 시 QUESTION(안전 기본 → FAQ). */
    public String classify(String text) {
        try {
            Map<String, Object> request = Map.of(
                    "model", chatbotProps.getSpeechActModel(),
                    "stream", false,
                    "think", false,
                    "options", Map.of("temperature", 0.0, "num_predict", 8),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", text)));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("message")) {
                return QUESTION;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) response.get("message");
            Object content = message.get("content");
            String out = content == null ? "" : content.toString().toUpperCase();
            if (out.contains(COMMAND)) {
                return COMMAND;
            }
            return QUESTION;
        } catch (Exception e) {
            log.warn("화행 분류 실패(QUESTION 으로 안전 폴백): {}", e.getMessage());
            return QUESTION;
        }
    }
}
