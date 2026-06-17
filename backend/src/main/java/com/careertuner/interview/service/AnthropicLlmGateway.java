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
 * Anthropic Messages API 전송 게이트웨이(면접 LLM 1차 provider, Claude Haiku).
 *
 * <p>구조화 출력은 기대 스키마를 프롬프트에 임베드 + "순수 JSON 만 출력" 지시로 보장한다(JSON 모드 플래그가 없으므로).
 * 응답이 코드블록/잡설을 섞어도 펜스를 제거하고 파싱하며, 실패 시 상위 디스패처가 OpenAI 로 폴백한다.
 */
@Component
public class AnthropicLlmGateway implements InterviewLlmGateway {

    private static final int MAX_ATTEMPTS = 3;

    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AnthropicLlmGateway(AnthropicProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    /** 키 설정 여부 — 폴백 디스패처가 Claude 시도 가능 여부 판단에 쓴다. */
    public boolean available() {
        return properties.configured();
    }

    @Override
    public Result complete(Request request) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic API 키가 설정되어 있지 않습니다.");
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
        body.put("model", properties.getModel());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("system", request.systemPrompt());
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", userText)));
        body.put("temperature", 0.3);
        return body;
    }

    // ───── HTTP / 파싱 인프라 ─────

    private JsonNode post(Map<String, Object> requestBody) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.messagesUrl()))
                            .timeout(properties.getTimeout())
                            .header("x-api-key", properties.getApiKey())
                            .header("anthropic-version", properties.getVersion())
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
                            "Anthropic 요청에 실패했습니다. " + truncate(message, 300));
                } catch (IOException ex) {
                    if (attempt < MAX_ATTEMPTS) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic 응답을 처리하지 못했습니다.");
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic 요청에 실패했습니다.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic 요청을 구성하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic 요청이 중단되었습니다.");
        }
    }

    /** content[*] 의 text 블록을 이어붙여 JSON 으로 파싱한다. */
    private JsonNode parsePayload(JsonNode root) {
        StringBuilder builder = new StringBuilder();
        JsonNode content = root.path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText(""))) {
                    builder.append(block.path("text").asText(""));
                }
            }
        }
        String text = builder.toString().trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic 응답 본문이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic 응답이 JSON 형식이 아닙니다.");
        }
    }

    private InterviewOpenAiClient.Usage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        String model = root.path("model").asText(properties.getModel());
        return new InterviewOpenAiClient.Usage(model, inputTokens, outputTokens, inputTokens + outputTokens);
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
        // 429(rate limit), 529(overloaded), 5xx
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
