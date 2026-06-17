package com.careertuner.interview.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gemini {@code generateContent} 전송 게이트웨이(면접 LLM 1차 provider).
 *
 * <p>구조화 출력은 {@code generationConfig.responseMimeType=application/json}(JSON 강제) +
 * 기대 스키마를 프롬프트에 임베드하는 방식으로 보장한다. responseSchema 의 정확한 스키마 dialect 가
 * API 버전별로 갈려(OpenAPI 대문자 enum vs JSON Schema) 도박 대신 안정적인 JSON 모드를 택했다.
 * 응답이 코드블록/잡설을 섞어도 펜스를 제거하고 파싱한다.
 */
@Component
public class GeminiLlmGateway implements InterviewLlmGateway {

    private static final int MAX_ATTEMPTS = 3;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiLlmGateway(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    /** 키 설정 여부 — 폴백 디스패처가 Gemini 시도 가능 여부 판단에 쓴다. */
    public boolean available() {
        return properties.configured();
    }

    @Override
    public Result complete(Request request) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Gemini API 키가 설정되어 있지 않습니다.");
        }
        JsonNode root = post(buildBody(request));
        return new Result(parsePayload(root), usage(root));
    }

    private Map<String, Object> buildBody(Request request) {
        String schemaHint;
        try {
            schemaHint = objectMapper.writeValueAsString(request.jsonSchema());
        } catch (JacksonException ex) {
            schemaHint = "{}";
        }
        String userText = request.userPrompt()
                + "\n\n반드시 아래 JSON 스키마를 만족하는 JSON 객체 하나만 출력하라. "
                + "코드블록·설명·여는말 없이 순수 JSON 만 출력한다.\nJSON 스키마:\n" + schemaHint;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.systemPrompt()))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userText)))));
        body.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.3));
        return body;
    }

    // ───── HTTP / 파싱 인프라 ─────

    private JsonNode post(Map<String, Object> requestBody) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.generateContentUrl()))
                            .timeout(properties.getTimeout())
                            .header("x-goog-api-key", properties.getApiKey())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return objectMapper.readTree(response.body());
                    }

                    String message = errorMessage(response.body());
                    if (attempt < MAX_ATTEMPTS && isRetryable(response.statusCode())) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "Gemini 요청에 실패했습니다. " + truncate(message, 300));
                } catch (IOException ex) {
                    if (attempt < MAX_ATTEMPTS) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Gemini 응답을 처리하지 못했습니다.");
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Gemini 요청에 실패했습니다.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Gemini 요청을 구성하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Gemini 요청이 중단되었습니다.");
        }
    }

    /** candidates[0].content.parts[*].text 를 이어붙여 JSON 으로 파싱한다. */
    private JsonNode parsePayload(JsonNode root) {
        StringBuilder builder = new StringBuilder();
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                String text = part.path("text").asText("");
                if (!text.isBlank()) {
                    builder.append(text);
                }
            }
        }
        String text = builder.toString().trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Gemini 응답 본문이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Gemini 응답이 JSON 형식이 아닙니다.");
        }
    }

    private InterviewOpenAiClient.Usage usage(JsonNode root) {
        JsonNode usage = root.path("usageMetadata");
        int inputTokens = usage.path("promptTokenCount").asInt(0);
        int outputTokens = usage.path("candidatesTokenCount").asInt(0);
        int totalTokens = usage.path("totalTokenCount").asInt(inputTokens + outputTokens);
        String model = root.path("modelVersion").asText(properties.getModel());
        return new InterviewOpenAiClient.Usage(model, inputTokens, outputTokens, totalTokens);
    }

    private String errorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText("");
            return message.isBlank() ? body : message;
        } catch (JacksonException ex) {
            return body;
        }
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private void sleepBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(attempt * 1000L);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
