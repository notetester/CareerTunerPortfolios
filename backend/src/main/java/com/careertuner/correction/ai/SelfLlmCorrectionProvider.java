package com.careertuner.correction.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;
import com.careertuner.correction.ai.CorrectionAiProperties.Self;
import com.careertuner.correction.ai.prompt.CorrectionPromptCatalog;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class SelfLlmCorrectionProvider implements CorrectionAiProvider {

    private final CorrectionAiProperties properties;
    private final ObjectMapper objectMapper;
    private final CorrectionAiPayloadParser payloadParser;
    private final HttpClient httpClient;

    public SelfLlmCorrectionProvider(
            CorrectionAiProperties properties,
            ObjectMapper objectMapper,
            CorrectionAiPayloadParser payloadParser
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.payloadParser = payloadParser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getSelf().getTimeout())
                .build();
    }

    @Override
    public CorrectionPayload correct(CorrectionCommand command) {
        Self self = properties.getSelf();
        if (!self.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction self LLM base-url is not configured.");
        }
        JsonNode root = withRetry(Math.max(1, self.getMaxRetries() + 1),
                Math.max(0, self.getRetryBackoff().toMillis()),
                () -> sendOnce(self, requestBody(command)));
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        return payloadParser.parsePayload(content, usage(root, self.getModel()));
    }

    private String requestBody(CorrectionCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        Self self = properties.getSelf();
        body.put("model", self.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", CorrectionPromptCatalog.SYSTEM_PROMPT),
                Map.of("role", "user", "content", OpenAiCorrectionProvider.CorrectionPromptBuilder.userPrompt(command))));
        body.put("temperature", self.getTemperature());
        body.put("max_tokens", self.getMaxTokens());
        body.put("response_format", Map.of("type", "json_object"));
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction self LLM request could not be serialized.");
        }
    }

    private JsonNode sendOnce(Self self, String payload) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(chatCompletionsUrl(self.getBaseUrl())))
                    .timeout(self.getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (self.getApiKey() != null && !self.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + self.getApiKey());
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 500) {
                throw new SelfLlmTransientException("Correction self LLM request failed (" + status + ").");
            }
            if (status < 200 || status >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction self LLM request failed (" + status + ").");
            }
            return objectMapper.readTree(response.body());
        } catch (JacksonException ex) {
            throw new SelfLlmTransientException("Correction self LLM response is not valid JSON.");
        } catch (IOException ex) {
            throw new SelfLlmTransientException("Correction self LLM communication failed.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction self LLM request was interrupted.");
        }
    }

    private Usage usage(JsonNode root, String defaultModel) {
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("prompt_tokens").asInt(usage.path("input_tokens").asInt(0));
        int outputTokens = usage.path("completion_tokens").asInt(usage.path("output_tokens").asInt(0));
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        String model = root.path("model").asText(defaultModel);
        return new Usage(model, inputTokens, outputTokens, totalTokens);
    }

    private String chatCompletionsUrl(String baseUrl) {
        String base = baseUrl.replaceAll("/+$", "");
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        return base + "/v1/chat/completions";
    }

    static <T> T withRetry(int attempts, long backoffMs, Supplier<T> attempt) {
        SelfLlmTransientException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return attempt.get();
            } catch (SelfLlmTransientException ex) {
                last = ex;
                if (i < attempts - 1 && backoffMs > 0) {
                    try {
                        Thread.sleep(backoffMs * (i + 1L));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                }
            }
        }
        throw last;
    }

    static class SelfLlmTransientException extends RuntimeException {
        SelfLlmTransientException(String message) {
            super(message);
        }
    }
}
