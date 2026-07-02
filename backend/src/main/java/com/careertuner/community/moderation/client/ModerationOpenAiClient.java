package com.careertuner.community.moderation.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.applicationcase.service.OpenAiProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 커뮤니티 검열/태그/추출 폴백 provider(OpenAI, 3차).
 * 자체 Ollama·Claude 실패 시 {@link ModerationLlmGateway} 가 호출한다.
 *
 * <p>OpenAI Responses API 를 쓰되, 검열/태그/추출의 임의 스키마가 strict json_schema 요건(모든 필드 required·
 * additionalProperties:false)을 만족하지 않으므로 스키마를 프롬프트에 임베드 + "순수 JSON 만" 지시로 유도한다.
 * 파싱은 호출부가 하므로 JSON 텍스트만 반환한다. 키·모델은 공유 {@link OpenAiProperties}(careertuner.openai) 재사용.
 */
@Component
public class ModerationOpenAiClient {

    private static final int MAX_ATTEMPTS = 3;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ModerationOpenAiClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    /** 키 설정 여부 — 게이트웨이가 OpenAI 폴백 시도 가능 여부 판단에 쓴다. */
    public boolean available() {
        return properties.configured();
    }

    public String chat(String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        String schemaHint;
        try {
            schemaHint = objectMapper.writeValueAsString(jsonSchema);
        } catch (JacksonException ex) {
            schemaHint = "{}";
        }
        String userWithSchema = userText
                + "\n\n반드시 아래 JSON 스키마를 만족하는 JSON 객체 하나만 출력하라. "
                + "코드블록·설명·여는말 없이 순수 JSON 만 출력한다.\nJSON 스키마:\n" + schemaHint;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        // 추론 모델(gpt-5/o-시리즈)은 기본 추론량이 커서 느리므로 검열엔 낮은 추론량으로 응답 안정성 확보.
        if (isReasoningModel(properties.getModel())) {
            body.put("reasoning", Map.of("effort", "low"));
        }
        body.put("input", List.of(
                message("system", systemPrompt),
                message("user", userWithSchema)));

        return extractText(post(body));
    }

    private Map<String, Object> message(String role, String text) {
        return Map.of("role", role, "content", List.of(Map.of("type", "input_text", "text", text)));
    }

    private JsonNode post(Map<String, Object> requestBody) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(properties.responsesUrl()))
                            .timeout(properties.getTimeout())
                            .header("Authorization", "Bearer " + properties.getApiKey())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return objectMapper.readTree(response.body());
                    }
                    if (attempt < MAX_ATTEMPTS && isRetryable(response.statusCode())) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new IllegalStateException("OpenAI 검열 요청 실패: HTTP " + response.statusCode());
                } catch (IOException ex) {
                    if (attempt < MAX_ATTEMPTS) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new IllegalStateException("OpenAI 검열 응답 처리 실패", ex);
                }
            }
            throw new IllegalStateException("OpenAI 검열 요청 실패");
        } catch (JacksonException ex) {
            throw new IllegalStateException("OpenAI 검열 요청 구성 실패", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI 검열 요청 중단됨", ex);
        }
    }

    /** output_text 우선, 없으면 output[*].content[*].text 를 이어붙이고 코드펜스를 제거한다. */
    private String extractText(JsonNode root) {
        StringBuilder builder = new StringBuilder();
        String direct = root.path("output_text").asText("");
        if (direct.isBlank()) {
            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) {
                    JsonNode content = item.path("content");
                    if (content.isArray()) {
                        for (JsonNode part : content) {
                            builder.append(part.path("text").asText(""));
                        }
                    }
                }
            }
        } else {
            builder.append(direct);
        }
        String text = builder.toString().trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (text.isBlank()) {
            throw new IllegalStateException("OpenAI 검열 응답이 비어 있습니다");
        }
        return text;
    }

    private boolean isReasoningModel(String model) {
        if (model == null) {
            return false;
        }
        String m = model.toLowerCase(Locale.ROOT);
        return m.startsWith("gpt-5") || m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4");
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private void sleepBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(attempt * 1000L);
    }
}
