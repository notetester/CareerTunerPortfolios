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
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;
import com.careertuner.correction.ai.prompt.CorrectionPromptCatalog;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnthropicCorrectionProvider implements CorrectionAiProvider {

    private final CorrectionAnthropicProperties properties;
    private final CorrectionAiProperties correctionProperties;
    private final ObjectMapper objectMapper;
    private final SelfCorrectionOutputParser outputParser;
    private final HttpClient httpClient;

    public AnthropicCorrectionProvider(
            CorrectionAnthropicProperties properties,
            CorrectionAiProperties correctionProperties,
            ObjectMapper objectMapper,
            SelfCorrectionOutputParser outputParser
    ) {
        this.properties = properties;
        this.correctionProperties = correctionProperties;
        this.objectMapper = objectMapper;
        this.outputParser = outputParser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    public boolean configured() {
        return properties.configured();
    }

    @Override
    public CorrectionPayload correct(CorrectionCommand command) {
        if (!configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic API key is not configured.");
        }
        SelfCorrectionInput input = command.selfInput() == null
                ? SelfCorrectionInput.minimal(command)
                : command.selfInput();
        JsonNode root = post(requestBody(input));
        SelfCorrectionOutput output = outputParser.parse(responseText(root), input.taskType());
        return new CorrectionPayload(
                output.correctedText(),
                output.summary(),
                output.riskFlags(),
                output.changes().stream().map(SelfCorrectionOutput.Change::reason).toList(),
                output.recommendedKeywords(),
                usage(root),
                output.toResultMap());
    }

    private Map<String, Object> requestBody(SelfCorrectionInput input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", Math.max(256, correctionProperties.getSelf().getMaxTokens()));
        body.put("system", CorrectionPromptCatalog.SELF_SYSTEM_PROMPT);
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", json(input.toRequestMap()))));
        body.put("temperature", correctionProperties.getSelf().getTemperature());
        return body;
    }

    private JsonNode post(Map<String, Object> requestBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.messagesUrl()))
                    .timeout(properties.getTimeout())
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", properties.getVersion())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AnthropicCorrectionCallException(
                        "Anthropic correction request failed (" + response.statusCode() + ").");
            }
            return objectMapper.readTree(response.body());
        } catch (HttpTimeoutException ex) {
            throw new AnthropicCorrectionCallException("Anthropic correction request timed out.", ex);
        } catch (JacksonException ex) {
            throw new AnthropicCorrectionCallException("Anthropic correction JSON processing failed.", ex);
        } catch (IOException ex) {
            throw new AnthropicCorrectionCallException("Anthropic correction communication failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AnthropicCorrectionCallException("Anthropic correction request was interrupted.", ex);
        }
    }

    private String responseText(JsonNode root) {
        StringBuilder text = new StringBuilder();
        JsonNode content = root.path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText(""))) {
                    text.append(block.path("text").asText(""));
                }
            }
        }
        if (text.isEmpty()) {
            throw new AnthropicCorrectionCallException("Anthropic correction response text is empty.");
        }
        return text.toString();
    }

    private Usage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        return new Usage(
                root.path("model").asText(properties.getModel()),
                inputTokens,
                outputTokens,
                inputTokens + outputTokens);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new AnthropicCorrectionCallException("Anthropic correction request could not be serialized.", ex);
        }
    }

    static class AnthropicCorrectionCallException extends RuntimeException {
        AnthropicCorrectionCallException(String message) {
            super(message);
        }

        AnthropicCorrectionCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
