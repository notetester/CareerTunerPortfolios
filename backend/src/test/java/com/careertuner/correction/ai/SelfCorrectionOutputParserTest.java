package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class SelfCorrectionOutputParserTest {

    private final SelfCorrectionOutputParser parser = new SelfCorrectionOutputParser(new ObjectMapper());

    @Test
    @DisplayName("parses Qwen thinking text followed by the exact trained JSON schema")
    void parse_acceptsThinkingPrefix() {
        SelfCorrectionOutput output = parser.parse(
                "<think>{검토용 메모}</think>\n" + validJson(), "SELF_INTRO_CORRECTION");

        assertThat(output.correctedText()).isEqualTo("개선된 문장");
        assertThat(output.changes()).hasSize(1);
        assertThat(output.confidence()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("rejects extra root keys")
    void parse_rejectsExtraKeys() {
        String invalid = validJson().replace("\"confidence\":0.8", "\"confidence\":0.8,\"target_role\":\"개발자\"");

        assertThatThrownBy(() -> parser.parse(invalid, "SELF_INTRO_CORRECTION"))
                .isInstanceOf(SelfCorrectionOutputParser.InvalidOutputException.class)
                .hasMessageContaining("missing or extra keys");
    }

    @Test
    @DisplayName("rejects task mismatch and invented facts")
    void parse_rejectsTaskMismatchAndAddedFacts() {
        assertThatThrownBy(() -> parser.parse(validJson(), "INTERVIEW_ANSWER_CORRECTION"))
                .hasMessageContaining("task_type does not match");

        String invented = validJson().replace("\"added_facts\":[]", "\"added_facts\":[\"수상\"]");
        assertThatThrownBy(() -> parser.parse(invented, "SELF_INTRO_CORRECTION"))
                .hasMessageContaining("added_facts must be empty");
    }

    private String validJson() {
        return """
                {
                  "status":"ok",
                  "task_type":"SELF_INTRO_CORRECTION",
                  "corrected_text":"개선된 문장",
                  "summary":"요약",
                  "changes":[{
                    "before":"원문",
                    "after":"개선된 문장",
                    "reason":"표현을 구체화했다",
                    "evidence_source":"original_text"
                  }],
                  "risk_flags":[],
                  "preserved_meaning":true,
                  "added_facts":[],
                  "recommended_keywords":["문서 정리"],
                  "confidence":0.8
                }
                """;
    }
}
