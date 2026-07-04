package com.careertuner.correction.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;
import com.careertuner.correction.ai.CorrectionAiProperties.Self;
import com.careertuner.correction.ai.SelfCorrectionOutputParser.InvalidOutputException;
import com.careertuner.correction.ai.prompt.CorrectionPromptCatalog;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SelfLlmCorrectionProvider implements CorrectionAiProvider {

    private static final int MAX_REPAIR_OUTPUT_CHARS = 4_000;

    private final CorrectionAiProperties properties;
    private final ObjectMapper objectMapper;
    private final SelfCorrectionOutputParser outputParser;
    private final GpuPermitGate gpuPermitGate;
    private final HttpClient httpClient;

    public SelfLlmCorrectionProvider(
            CorrectionAiProperties properties,
            ObjectMapper objectMapper,
            SelfCorrectionOutputParser outputParser,
            GpuPermitGate gpuPermitGate
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.outputParser = outputParser;
        this.gpuPermitGate = gpuPermitGate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getSelf().getConnectTimeout())
                .build();
    }

    @Override
    public CorrectionPayload correct(CorrectionCommand command) {
        return correct(command, properties.getSelf().getModel(), properties.getSelf().getTimeout(), null);
    }

    public CorrectionPayload correct(CorrectionCommand command, String model, Duration timeout) {
        return correct(command, model, timeout, null);
    }

    public CorrectionPayload correct(
            CorrectionCommand command,
            String model,
            Duration timeout,
            RepairContext repairContext
    ) {
        Self self = properties.getSelf();
        if (!self.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction self LLM base-url is not configured.");
        }
        JsonNode root = sendOnce(self, requestBody(command, model, repairContext), timeout);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        SelfCorrectionInput input = command.selfInput() == null
                ? SelfCorrectionInput.minimal(command)
                : command.selfInput();
        SelfCorrectionOutput output;
        try {
            output = outputParser.parse(content, input.taskType());
            if (!output.preservedMeaning()) {
                throw new InvalidOutputException(
                        "Correction self LLM output validation failed: preserved_meaning must be true.");
            }
            validateContract(input, output);
        } catch (InvalidOutputException ex) {
            throw ex.withPreviousOutput(repairOutput(content));
        }
        return new CorrectionPayload(
                output.correctedText(),
                output.summary(),
                output.riskFlags(),
                output.changes().stream().map(SelfCorrectionOutput.Change::reason).toList(),
                output.recommendedKeywords(),
                usage(root, model),
                output.toResultMap());
    }

    private String requestBody(CorrectionCommand command, String model, RepairContext repairContext) {
        SelfCorrectionInput input = command.selfInput() == null
                ? SelfCorrectionInput.minimal(command)
                : command.selfInput();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", CorrectionPromptCatalog.SELF_SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", json(input.toRequestMap())));
        if (repairContext != null) {
            messages.add(Map.of(
                    "role", "user",
                    "content", CorrectionPromptCatalog.selfRepairPrompt(
                            repairContext.validationError(), repairContext.previousOutput())));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", repairContext == null ? properties.getSelf().getTemperature() : 0.0);
        body.put("max_tokens", properties.getSelf().getMaxTokens());
        body.put("response_format", responseFormat(input.taskType()));
        return json(body);
    }

    private Map<String, Object> responseFormat(String taskType) {
        Map<String, Object> changeProperties = new LinkedHashMap<>();
        changeProperties.put("before", stringSchema());
        changeProperties.put("after", stringSchema());
        changeProperties.put("reason", stringSchema());
        changeProperties.put("evidence_source", Map.of(
                "type", "string",
                "enum", List.of("original_text", "user_profile_facts", "job_context")));

        Map<String, Object> changeSchema = objectSchema(
                changeProperties,
                List.of("before", "after", "reason", "evidence_source"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of("type", "string", "enum", List.of("ok")));
        properties.put("task_type", Map.of("type", "string", "enum", List.of(taskType)));
        properties.put("corrected_text", stringSchema());
        properties.put("summary", stringSchema());
        properties.put("changes", Map.of(
                "type", "array",
                "minItems", 3,
                "items", changeSchema));
        properties.put("risk_flags", stringArraySchema());
        properties.put("preserved_meaning", Map.of("type", "boolean", "const", true));
        properties.put("added_facts", Map.of(
                "type", "array",
                "maxItems", 0,
                "items", Map.of("type", "string")));
        properties.put("recommended_keywords", stringArraySchema());
        properties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));

        Map<String, Object> schema = objectSchema(properties, List.of(
                "status", "task_type", "corrected_text", "summary", "changes",
                "risk_flags", "preserved_meaning", "added_facts", "recommended_keywords", "confidence"));
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "e_correction_result",
                        "strict", true,
                        "schema", schema));
    }

    private Map<String, Object> objectSchema(Map<String, Object> schemaProperties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", schemaProperties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string", "minLength", 1);
    }

    private Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
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
            HttpResponse<String> response;
            try (GpuPermitGate.GpuPermit permit = gpuPermitGate.acquire("correction")) {
                response = httpClient.send(
                        builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }
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

    private String repairOutput(String content) {
        if (content == null || content.length() <= MAX_REPAIR_OUTPUT_CHARS) {
            return content == null ? "" : content;
        }
        return content.substring(0, MAX_REPAIR_OUTPUT_CHARS);
    }

    private void validateContract(SelfCorrectionInput input, SelfCorrectionOutput output) {
        if (output.changes().size() < 3) {
            throw new InvalidOutputException(
                    "Correction self LLM output validation failed: changes must contain at least three items.");
        }
        Map<String, Object> constraints = input.constraints() == null ? Map.of() : input.constraints();
        int length = output.correctedText().length();
        int minChars = intConstraint(constraints, "min_chars", 1);
        int maxChars = intConstraint(constraints, "max_chars", Integer.MAX_VALUE);
        if (length < minChars) {
            throw new InvalidOutputException(
                    "Correction self LLM output validation failed: corrected_text is shorter than min_chars "
                            + minChars + ".");
        }
        if (length > maxChars) {
            throw new InvalidOutputException(
                    "Correction self LLM output validation failed: corrected_text exceeds max_chars "
                            + maxChars + ".");
        }
        if (Boolean.TRUE.equals(constraints.get("preserve_paragraphs"))) {
            int sourceParagraphs = paragraphCount(input.originalText());
            int outputParagraphs = paragraphCount(output.correctedText());
            if (sourceParagraphs > 1 && outputParagraphs < sourceParagraphs) {
                throw new InvalidOutputException(
                        "Correction self LLM output validation failed: corrected_text must preserve at least "
                                + sourceParagraphs + " paragraphs.");
            }
        }
    }

    private int intConstraint(Map<String, Object> constraints, String key, int defaultValue) {
        Object value = constraints.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private int paragraphCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("(?:\\r?\\n)\\s*(?:\\r?\\n)+").length;
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

    record RepairContext(String validationError, String previousOutput) {
        RepairContext {
            validationError = validationError == null ? "" : validationError;
            previousOutput = previousOutput == null ? "" : previousOutput;
        }
    }
}
