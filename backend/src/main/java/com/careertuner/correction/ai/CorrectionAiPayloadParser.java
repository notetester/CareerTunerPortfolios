package com.careertuner.correction.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class CorrectionAiPayloadParser {

    private final ObjectMapper objectMapper;

    public CorrectionAiPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CorrectionPayload parsePayload(String text, Usage usage) {
        return toPayload(parseOutputJson(text), usage);
    }

    public CorrectionPayload toPayload(JsonNode payload, Usage usage) {
        String improvedText = payload.path("improvedText").asText("").trim();
        if (improvedText.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI correction result is empty.");
        }
        return new CorrectionPayload(
                improvedText,
                payload.path("summary").asText(""),
                stringList(payload.path("issues")),
                stringList(payload.path("changeReasons")),
                stringList(payload.path("suggestions")),
                usage);
    }

    private JsonNode parseOutputJson(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        normalized = extractJsonSpan(normalized);
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI correction response text is empty.");
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI correction result is not valid JSON.");
        }
    }

    static String extractJsonSpan(String text) {
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        int start = objStart < 0 ? arrStart : (arrStart < 0 ? objStart : Math.min(objStart, arrStart));
        int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }
}
