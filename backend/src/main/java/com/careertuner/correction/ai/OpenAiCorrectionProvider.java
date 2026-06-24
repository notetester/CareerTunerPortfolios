package com.careertuner.correction.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;
import com.careertuner.correction.ai.prompt.CorrectionPromptCatalog;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class OpenAiCorrectionProvider implements CorrectionAiProvider {

    private static final int MAX_ATTEMPTS = 3;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final CorrectionAiPayloadParser payloadParser;
    private final HttpClient httpClient;

    public OpenAiCorrectionProvider(
            OpenAiProperties properties,
            ObjectMapper objectMapper,
            CorrectionAiPayloadParser payloadParser
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.payloadParser = payloadParser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    @Override
    public CorrectionPayload correct(CorrectionCommand command) {
        JsonNode root = post(structuredRequest(
                "correction_result",
                correctionSchema(),
                CorrectionPromptCatalog.SYSTEM_PROMPT,
                CorrectionPromptBuilder.userPrompt(command)));
        return payloadParser.parsePayload(outputText(root), usage(root));
    }

    private JsonNode post(Map<String, Object> requestBody) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI API key is not configured.");
        }
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

                    String message = errorMessage(response.body());
                    if (attempt < MAX_ATTEMPTS && isRetryable(response.statusCode(), message)) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "OpenAI request failed. " + truncate(message, 300));
                } catch (IOException ex) {
                    log.warn("Correction OpenAI request I/O failure on attempt {}/{}", attempt, MAX_ATTEMPTS, ex);
                    if (ex instanceof HttpTimeoutException || containsIgnoreCase(ex.getMessage(), "timeout")) {
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                                "OpenAI request timed out after %d seconds.".formatted(properties.getTimeout().toSeconds()));
                    }
                    if (attempt < MAX_ATTEMPTS) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI API communication failed.");
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI request failed.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI request or response JSON could not be parsed.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI request was interrupted.");
        }
    }

    private Map<String, Object> structuredRequest(String name, Map<String, Object> schema, String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", List.of(
                message("system", List.of(inputText(systemPrompt))),
                message("user", List.of(inputText(userPrompt)))));
        body.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", name,
                        "strict", true,
                        "schema", schema)));
        return body;
    }

    private Map<String, Object> message(String role, List<Map<String, Object>> content) {
        return Map.of("role", role, "content", content);
    }

    private Map<String, Object> inputText(String text) {
        return Map.of("type", "input_text", "text", text);
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
        if (builder.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI correction response text is empty.");
        }
        return builder.toString();
    }

    private Usage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        String model = root.path("model").asText(properties.getModel());
        return new Usage(model, inputTokens, outputTokens, totalTokens);
    }

    private Map<String, Object> correctionSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("improvedText", stringSchema());
        props.put("summary", stringSchema());
        props.put("issues", stringArraySchema());
        props.put("changeReasons", stringArraySchema());
        props.put("suggestions", stringArraySchema());
        return objectSchema(props, List.of("improvedText", "summary", "issues", "changeReasons", "suggestions"));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }

    private boolean isRetryable(int statusCode, String message) {
        return statusCode == 408 || statusCode == 409 || statusCode == 429 || statusCode >= 500
                || containsIgnoreCase(message, "timeout")
                || containsIgnoreCase(message, "temporarily");
    }

    private String errorMessage(String responseBody) {
        if (isBlank(responseBody)) {
            return "empty error response";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("error").path("message").asText("");
            return message.isBlank() ? truncate(responseBody, 500) : message;
        } catch (JacksonException ex) {
            return truncate(responseBody, 500);
        }
    }

    private void sleepBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(300L * attempt);
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static class CorrectionPromptBuilder {
        private CorrectionPromptBuilder() {
        }

        static String userPrompt(CorrectionCommand command) {
            ApplicationCase applicationCase = command.applicationCase();
            String caseContext = applicationCase == null
                    ? "No application case was selected."
                    : """
                    Company: %s
                    Job title: %s
                    """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle());
            String question = isBlankStatic(command.questionText()) ? "" : "\nQuestion or prompt:\n" + command.questionText() + "\n";
            return """
                    Correction type: %s
                    Source type: %s

                    Application context:
                    %s
                    %s
                    Original text:
                    %s
                    """.formatted(
                    command.correctionType(),
                    command.sourceType(),
                    caseContext,
                    question,
                    command.originalText());
        }

        private static boolean isBlankStatic(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
