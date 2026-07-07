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
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * analysis 계열 AI 의 Claude(Haiku) 구조화 출력 클라이언트 — {@link CareerAnalysisOpenAiClient} 의 형제 어댑터.
 *
 * <p>OpenAI 클라이언트와 같은 {@link CareerAnalysisOpenAiClient.StructuredResponse} 를 반환하므로,
 * 도메인 서비스는 전송(OpenAI/Anthropic)만 바꿔 동일한 파싱 로직을 재사용할 수 있다.
 * Anthropic 은 json_schema 강제 모드가 없어, 기대 스키마를 프롬프트에 임베드하고 "순수 JSON 만 출력" 으로
 * 구조화를 유도한다. 응답이 코드블록을 섞어도 펜스를 제거하고 파싱하며, 실패 시 상위 디스패처가 OpenAI 로 폴백한다.
 */
@Service
public class CareerAnalysisAnthropicClient {

    private static final int MAX_ATTEMPTS = 3;

    private final CareerAnalysisAnthropicProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CareerAnalysisAnthropicClient(CareerAnalysisAnthropicProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    public boolean configured() {
        return properties.configured();
    }

    /**
     * @param name OpenAI 클라이언트와 시그니처를 맞추기 위한 인자. Anthropic 은 json_schema name 이 없어 사용하지 않는다.
     */
    public CareerAnalysisOpenAiClient.StructuredResponse request(String name,
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
     *
     * @param name OpenAI 클라이언트와 시그니처를 맞추기 위한 인자. Anthropic 은 json_schema name 이 없어 사용하지 않는다.
     */
    public CareerAnalysisOpenAiClient.StructuredResponse request(String name,
                                                                 Map<String, Object> schema,
                                                                 String systemPrompt,
                                                                 String userPrompt,
                                                                 Duration perAttemptTimeout,
                                                                 long chainDeadlineNanos) {
        if (!configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic API 키가 설정되어 있지 않습니다.");
        }

        String schemaHint;
        try {
            schemaHint = objectMapper.writeValueAsString(schema);
        } catch (JacksonException ex) {
            schemaHint = "{}";
        }
        String userText = userPrompt
                + "\n\n반드시 아래 JSON 스키마를 만족하는 JSON 객체 하나만 출력하라. "
                + "코드블록·설명·여는말 없이 순수 JSON 만 출력한다.\nJSON 스키마:\n" + schemaHint;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", userText)));
        body.put("temperature", 0.3);

        JsonNode root = post(body, perAttemptTimeout, chainDeadlineNanos);
        return new CareerAnalysisOpenAiClient.StructuredResponse(parsePayload(root), usage(root));
    }

    private JsonNode post(Map<String, Object> requestBody, Duration perAttemptTimeout, long chainDeadlineNanos) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(properties.messagesUrl()))
                            .timeout(perAttemptTimeout)
                            .header("x-api-key", properties.getApiKey())
                            .header("anthropic-version", properties.getVersion())
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
                    if (attempt < MAX_ATTEMPTS && System.nanoTime() < chainDeadlineNanos && retryable(response.statusCode())) {
                        sleep(attempt);
                        continue;
                    }
                    throw new BusinessException(
                            ErrorCode.INTERNAL_ERROR,
                            "Anthropic 요청에 실패했습니다. " + truncate(message, 300));
                } catch (IOException ex) {
                    if (attempt < MAX_ATTEMPTS && System.nanoTime() < chainDeadlineNanos) {
                        sleep(attempt);
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

    private CareerAnalysisAiUsage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        String model = root.path("model").asText(properties.getModel());
        return new CareerAnalysisAiUsage(model, inputTokens, outputTokens, inputTokens + outputTokens, false);
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

    private boolean retryable(int statusCode) {
        // 429(rate limit), 529(overloaded), 5xx
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
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
}
