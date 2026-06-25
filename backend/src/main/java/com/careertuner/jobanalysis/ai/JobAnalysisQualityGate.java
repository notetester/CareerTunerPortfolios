package com.careertuner.jobanalysis.ai;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobAnalysisQualityGate {

    private static final int MIN_SUMMARY_LENGTH = 30;

    private final ObjectMapper objectMapper;

    public boolean isAcceptable(JobAnalysisPayload result) {
        if (result == null) {
            log.debug("Quality gate: result is null");
            return false;
        }

        if (isBlank(result.summary()) || result.summary().length() < MIN_SUMMARY_LENGTH) {
            log.debug("Quality gate: summary too short ({})", result.summary());
            return false;
        }

        int skillCount = countJsonArrayItems(result.requiredSkills())
                + countJsonArrayItems(result.preferredSkills());
        if (skillCount == 0) {
            log.debug("Quality gate: no skills extracted");
            return false;
        }

        return true;
    }

    private int countJsonArrayItems(String jsonArray) {
        if (isBlank(jsonArray)) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(jsonArray);
            return node.isArray() ? node.size() : 0;
        } catch (JacksonException ex) {
            return 0;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
