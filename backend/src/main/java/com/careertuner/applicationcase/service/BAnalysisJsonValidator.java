package com.careertuner.applicationcase.service;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class BAnalysisJsonValidator {

    private static final String INVALID_REVIEW_JSON_MESSAGE =
            "\uAD6C\uC870\uD654\uB41C \uBD84\uC11D \uAC80\uD1A0 JSON \uD615\uC2DD\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4.";

    private final ObjectMapper objectMapper;

    public String validateEvidence(String value) {
        return validateObjectArray(value, "field", "quote");
    }

    public String validateAmbiguousConditions(String value) {
        return validateObjectArray(value, "condition", "assumption");
    }

    public String validateVerifiedFacts(String value) {
        return validateObjectArray(value, "fact", "source");
    }

    public String validateAiInferences(String value) {
        return validateObjectArray(value, "inference", "basis");
    }

    private String validateObjectArray(String value, String... requiredKeys) {
        JsonNode root = parse(value);
        if (!root.isArray()) {
            throw invalidInput();
        }
        for (JsonNode item : root) {
            validateObject(item, requiredKeys);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException ex) {
            throw invalidInput();
        }
    }

    private JsonNode parse(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JacksonException ex) {
            throw invalidInput();
        }
    }

    private void validateObject(JsonNode item, String... requiredKeys) {
        if (!item.isObject()) {
            throw invalidInput();
        }
        for (String key : requiredKeys) {
            JsonNode field = item.path(key);
            if (field.isMissingNode() || !field.isString()) {
                throw invalidInput();
            }
        }
    }

    private BusinessException invalidInput() {
        return new BusinessException(ErrorCode.INVALID_INPUT, INVALID_REVIEW_JSON_MESSAGE);
    }
}
