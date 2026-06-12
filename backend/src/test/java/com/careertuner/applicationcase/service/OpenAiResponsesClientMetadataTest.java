package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobPostingMetadataPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenAiResponsesClientMetadataTest {

    @Test
    void parseJobPostingMetadataConvertsInvalidOrBlankDatesToNull() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode payload = objectMapper.readTree("""
                {
                  "companyName": "Acme",
                  "jobTitle": "Backend Engineer",
                  "postingDate": "2026-06-01",
                  "deadlineDate": "not clear"
                }
                """);

        JobPostingMetadataPayload result = OpenAiResponsesClient.parseJobPostingMetadataPayload(
                payload,
                new Usage("gpt-test", 10, 5, 15));

        assertThat(result.companyName()).isEqualTo("Acme");
        assertThat(result.jobTitle()).isEqualTo("Backend Engineer");
        assertThat(result.postingDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.deadlineDate()).isNull();
        assertThat(result.usage().totalTokens()).isEqualTo(15);
    }
}
