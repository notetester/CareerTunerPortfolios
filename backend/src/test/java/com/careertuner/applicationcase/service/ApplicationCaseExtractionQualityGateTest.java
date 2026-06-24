package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ApplicationCaseExtractionQualityGateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationCaseExtractionQualityGate qualityGate =
            new ApplicationCaseExtractionQualityGate(objectMapper);

    @Test
    void sharedFixtureStatusesMatchPythonWorkerContract() throws Exception {
        JsonNode cases = objectMapper.readTree(Files.readString(Path.of(
                "../ml/job-posting-worker/tests/fixtures/quality_gate_cases.json")));

        for (JsonNode testCase : cases) {
            String text = testCase.path("text").asText();
            String expectedStatus = testCase.path("expectedStatus").asText();

            ApplicationCaseExtractionQualityGate.QualityGateResult result =
                    qualityGate.evaluate("TEXT", null, text);

            assertThat(result.qualityStatus())
                    .as(testCase.path("name").asText())
                    .isEqualTo(expectedStatus);
        }
    }
}
