package com.careertuner.correction.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SelfCorrectionOutputParser {

    private static final Set<String> EVIDENCE_SOURCES = Set.of(
            "original_text", "user_profile_facts", "job_context");
    private static final String[] ROOT_KEYS = {
            "status", "task_type", "corrected_text", "summary", "changes",
            "risk_flags", "preserved_meaning", "added_facts", "recommended_keywords", "confidence"
    };
    private static final String[] CHANGE_KEYS = {"before", "after", "reason", "evidence_source"};

    private final ObjectMapper objectMapper;

    public SelfCorrectionOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SelfCorrectionOutput parse(String text, String expectedTaskType) {
        JsonNode root = parseJson(text);
        requireExactObject(root, ROOT_KEYS, "root");
        requireText(root, "status");
        if (!"ok".equals(root.path("status").asText())) {
            throw invalid("status must be ok");
        }
        String taskType = requireText(root, "task_type");
        if (!expectedTaskType.equals(taskType)) {
            throw invalid("task_type does not match the request");
        }
        String correctedText = requireText(root, "corrected_text");
        String summary = requireText(root, "summary");
        List<SelfCorrectionOutput.Change> changes = changes(root.path("changes"));
        List<String> riskFlags = stringList(root.path("risk_flags"), "risk_flags");
        List<String> addedFacts = stringList(root.path("added_facts"), "added_facts");
        if (!addedFacts.isEmpty()) {
            throw invalid("added_facts must be empty");
        }
        List<String> recommendedKeywords = stringList(
                root.path("recommended_keywords"), "recommended_keywords");
        if (!root.path("preserved_meaning").isBoolean()) {
            throw invalid("preserved_meaning must be boolean");
        }
        if (!root.path("confidence").isNumber()) {
            throw invalid("confidence must be numeric");
        }
        double confidence = root.path("confidence").asDouble();
        if (confidence < 0 || confidence > 1) {
            throw invalid("confidence must be between 0 and 1");
        }
        return new SelfCorrectionOutput(
                "ok", taskType, correctedText, summary, changes, riskFlags,
                root.path("preserved_meaning").asBoolean(), addedFacts, recommendedKeywords, confidence);
    }

    private JsonNode parseJson(String text) {
        String normalized = text == null ? "" : text.trim();
        normalized = normalized.replaceFirst("(?s)^\\s*<think>.*?</think>\\s*", "");
        normalized = CorrectionAiPayloadParser.extractJsonSpan(normalized);
        if (normalized.isBlank()) {
            throw invalid("response text is empty");
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (JacksonException ex) {
            throw invalid("response is not valid JSON");
        }
    }

    private List<SelfCorrectionOutput.Change> changes(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            throw invalid("changes must contain at least one item");
        }
        List<SelfCorrectionOutput.Change> values = new ArrayList<>();
        for (JsonNode item : node) {
            requireExactObject(item, CHANGE_KEYS, "changes item");
            String evidenceSource = requireText(item, "evidence_source");
            if (!EVIDENCE_SOURCES.contains(evidenceSource)) {
                throw invalid("changes evidence_source is invalid");
            }
            values.add(new SelfCorrectionOutput.Change(
                    requireText(item, "before"),
                    requireText(item, "after"),
                    requireText(item, "reason"),
                    evidenceSource));
        }
        return List.copyOf(values);
    }

    private void requireExactObject(JsonNode node, String[] keys, String label) {
        if (!node.isObject() || node.size() != keys.length) {
            throw invalid(label + " has missing or extra keys");
        }
        for (String key : keys) {
            if (!node.has(key)) {
                throw invalid(label + " is missing " + key);
            }
        }
    }

    private String requireText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.asText().trim().isBlank()) {
            throw invalid(key + " must be a non-empty string");
        }
        return value.asText().trim();
    }

    private List<String> stringList(JsonNode node, String label) {
        if (!node.isArray()) {
            throw invalid(label + " must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw invalid(label + " must contain strings only");
            }
            String value = item.asText().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private InvalidOutputException invalid(String reason) {
        return new InvalidOutputException("Correction self LLM output validation failed: " + reason + ".");
    }

    static class InvalidOutputException extends RuntimeException {
        private final String previousOutput;

        InvalidOutputException(String message) {
            this(message, "");
        }

        InvalidOutputException(String message, String previousOutput) {
            super(message);
            this.previousOutput = previousOutput == null ? "" : previousOutput;
        }

        InvalidOutputException withPreviousOutput(String output) {
            return new InvalidOutputException(getMessage(), output);
        }

        String previousOutput() {
            return previousOutput;
        }
    }
}
