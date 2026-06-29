package com.careertuner.correction.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;
import com.careertuner.correction.ai.CorrectionAiProperties.Self;
import com.careertuner.correction.ai.prompt.CorrectionPromptCatalog;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SelfLlmCorrectionProvider implements CorrectionAiProvider {

    private final CorrectionAiProperties properties;
    private final ObjectMapper objectMapper;
    private final SelfCorrectionOutputParser outputParser;
    private final HttpClient httpClient;

    public SelfLlmCorrectionProvider(
            CorrectionAiProperties properties,
            ObjectMapper objectMapper,
            SelfCorrectionOutputParser outputParser
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.outputParser = outputParser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getSelf().getConnectTimeout())
                .build();
    }

    @Override
    public CorrectionPayload correct(CorrectionCommand command) {
        return correct(command, properties.getSelf().getModel(), properties.getSelf().getTimeout());
    }

    public CorrectionPayload correct(CorrectionCommand command, String model, Duration timeout) {
        Self self = properties.getSelf();
        if (!self.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction self LLM base-url is not configured.");
        }
        JsonNode root = sendOnce(self, requestBody(command, model), timeout);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        SelfCorrectionInput input = command.selfInput() == null
                ? SelfCorrectionInput.minimal(command)
                : command.selfInput();
        SelfCorrectionOutput output = outputParser.parse(content, input.taskType());
        return new CorrectionPayload(
                output.correctedText(),
                output.summary(),
                output.riskFlags(),
                output.changes().stream().map(SelfCorrectionOutput.Change::reason).toList(),
                output.recommendedKeywords(),
                usage(root, model),
                output.toResultMap());
    }

    private String requestBody(CorrectionCommand command, String model) {
        SelfCorrectionInput input = command.selfInput() == null
                ? SelfCorrectionInput.minimal(command)
                : command.selfInput();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", CorrectionPromptCatalog.SELF_SYSTEM_PROMPT),
                Map.of("role", "user", "content", json(input.toRequestMap()))));
        body.put("temperature", properties.getSelf().getTemperature());
        body.put("max_tokens", properties.getSelf().getMaxTokens());
        body.put("response_format", Map.of("type", "json_object"));
        return json(body);
    }

    private JsonNode sendOnce(Self self, String payload, Duration timeout) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(chatCompletionsUrl(self.getBaseUrl())))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (self.getApiKey() != null && !self.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + self.getApiKey());
            }
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 500) {
                throw new SelfLlmCallException("Correction self LLM request failed (" + status + ").", true);
            }
            if (status < 200 || status >= 300) {
                throw new SelfLlmCallException("Correction self LLM request failed (" + status + ").", false);
            }
            return objectMapper.readTree(response.body());
        } catch (HttpTimeoutException ex) {
            throw new SelfLlmCallException("Correction self LLM request timed out.", false);
        } catch (JacksonException ex) {
            throw new SelfLlmCallException("Correction self LLM response is not valid JSON.", true);
        } catch (IOException ex) {
            throw new SelfLlmCallException("Correction self LLM communication failed.", false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction self LLM request was interrupted.");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction self LLM request could not be serialized.");
        }
    }

    private Usage usage(JsonNode root, String defaultModel) {
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("prompt_tokens").asInt(usage.path("input_tokens").asInt(0));
        int outputTokens = usage.path("completion_tokens").asInt(usage.path("output_tokens").asInt(0));
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        return new Usage(root.path("model").asText(defaultModel), inputTokens, outputTokens, totalTokens);
    }

    private String chatCompletionsUrl(String baseUrl) {
        String base = baseUrl.replaceAll("/+$", "");
        return base.endsWith("/v1") ? base + "/chat/completions" : base + "/v1/chat/completions";
    }

    static class SelfLlmCallException extends RuntimeException {
        private final boolean retrySameModel;

        SelfLlmCallException(String message, boolean retrySameModel) {
            super(message);
            this.retrySameModel = retrySameModel;
        }

        boolean retrySameModel() {
            return retrySameModel;
        }
    }
}
