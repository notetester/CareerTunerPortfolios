package com.careertuner.analysis.ai.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * C 담당 AI 12~18에서 공유하는 Responses API 구조화 출력 클라이언트.
 *
 * <p>전역 공통 AI 엔진은 팀장 소유이므로 변경하지 않고 C 소유 패키지 안에 둔다.
 * 다른 담당자는 이 구현을 수정하지 않고 각자 소유 도메인에 같은 형태의 어댑터를 둘 수 있다.
 */
@Service
public class CareerAnalysisOpenAiClient {

    private static final int MAX_ATTEMPTS = 3;

    private final CareerAnalysisAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CareerAnalysisOpenAiClient(CareerAnalysisAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    public boolean configured() {
        return properties.configured();
    }

    public StructuredResponse request(String name,
                                      Map<String, Object> schema,
                                      String systemPrompt,
                                      String userPrompt) {
        return request(name, schema, systemPrompt, userPrompt, properties.getTimeout(), Long.MAX_VALUE);
    }

    /**
     * 폴백 티어별로 시도당 타임아웃({@code perAttemptTimeout})을 보장하고, 체인 데드라인({@code chainDeadlineNanos})으로
     * 재시도만 유계화하는 오버로드. 첫 시도(attempt==1)는 데드라인과 무관하게 항상 수행하고, 이후 재시도만
     * {@code System.nanoTime() < chainDeadlineNanos} 를 추가로 요구한다. 데드라인이 지나면 기존 실패 예외로 떨어져
     * 상위 디스패처가 다음 티어로 캐스케이드한다.
     */
    public StructuredResponse request(String name,
                                      Map<String, Object> schema,
                                      String systemPrompt,
                                      String userPrompt,
                                      Duration perAttemptTimeout,
                                      long chainDeadlineNanos) {
        if (!configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI API 키가 설정되어 있지 않습니다.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", List.of(
                message("system", systemPrompt),
                message("user", userPrompt)));
        body.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", name,
                        "strict", true,
                        "schema", schema)));

        JsonNode root = post(body, perAttemptTimeout, chainDeadlineNanos);
        return new StructuredResponse(parseOutputJson(root), usage(root));
    }

    private JsonNode post(Map<String, Object> requestBody, Duration perAttemptTimeout, long chainDeadlineNanos) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(properties.responsesUrl()))
                            .timeout(perAttemptTimeout)
                            .header("Authorization", "Bearer " + properties.getApiKey())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> response = httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return objectMapper.readTree(response.body());
                    }

                    String message = errorMessage(response.body());
                    if (attempt < MAX_ATTEMPTS && System.nanoTime() < chainDeadlineNanos && retryable(response.statusCode(), message)) {
                        sleep(attempt);
                        continue;
                    }
                    throw new BusinessException(
                            ErrorCode.INTERNAL_ERROR,
                            "OpenAI 요청에 실패했습니다. " + truncate(message, 300));
                } catch (IOException ex) {
                    if (attempt < MAX_ATTEMPTS && System.nanoTime() < chainDeadlineNanos) {
                        sleep(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 응답을 처리하지 못했습니다.");
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 요청에 실패했습니다.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 요청을 구성하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 요청이 중단되었습니다.");
        }
    }

    private JsonNode parseOutputJson(JsonNode root) {
        String text = outputText(root).trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 응답 본문이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 분석 결과가 JSON 형식이 아닙니다.");
        }
    }

    private String outputText(JsonNode root) {
        String direct = root.path("output_text").asText("");
        if (!direct.isBlank()) {
            return direct;
        }
        StringBuilder builder = new StringBuilder();
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        String text = part.path("text").asText("");
                        if (!text.isBlank()) {
                            builder.append(text);
                        }
                    }
                }
            }
        }
        return builder.toString();
    }

    private CareerAnalysisAiUsage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        return new CareerAnalysisAiUsage(properties.getModel(), inputTokens, outputTokens, totalTokens, false);
    }

    private Map<String, Object> message(String role, String text) {
        return Map.of(
                "role", role,
                "content", List.of(Map.of("type", "input_text", "text", text)));
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

    private boolean retryable(int statusCode, String message) {
        if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
            return true;
        }
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lower.contains("timeout")
                || lower.contains("upstream connect")
                || lower.contains("disconnect/reset");
    }

    private void sleep(int attempt) throws InterruptedException {
        Thread.sleep(attempt * 1000L);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    public record StructuredResponse(JsonNode payload, CareerAnalysisAiUsage usage) {
    }
}
