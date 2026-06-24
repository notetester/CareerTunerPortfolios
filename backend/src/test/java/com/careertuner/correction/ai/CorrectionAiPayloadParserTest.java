package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;

import tools.jackson.databind.ObjectMapper;

class CorrectionAiPayloadParserTest {

    private final CorrectionAiPayloadParser parser = new CorrectionAiPayloadParser(new ObjectMapper());

    @Test
    @DisplayName("parses fenced JSON and validates required improvedText")
    void parsePayload_parsesFencedJson() {
        CorrectionPayload payload = parser.parsePayload("""
                ```json
                {
                  "improvedText": "개선된 문장",
                  "summary": "요약",
                  "issues": ["문제"],
                  "changeReasons": ["이유"],
                  "suggestions": ["제안"]
                }
                ```
                """, new Usage("careertuner-e-correction", 1, 2, 3));

        assertThat(payload.improvedText()).isEqualTo("개선된 문장");
        assertThat(payload.summary()).isEqualTo("요약");
        assertThat(payload.issues()).containsExactly("문제");
        assertThat(payload.usage().model()).isEqualTo("careertuner-e-correction");
    }

    @Test
    @DisplayName("throws when improvedText is blank")
    void parsePayload_throwsWhenImprovedTextBlank() {
        assertThatThrownBy(() -> parser.parsePayload("""
                {"improvedText":"","summary":"","issues":[],"changeReasons":[],"suggestions":[]}
                """, new Usage("model", 0, 0, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI correction result is empty");
    }

    @Test
    @DisplayName("extractJsonSpan strips text around model JSON")
    void extractJsonSpan_stripsTextAroundJson() {
        assertThat(CorrectionAiPayloadParser.extractJsonSpan("answer {\"improvedText\":\"x\"} done"))
                .isEqualTo("{\"improvedText\":\"x\"}");
        assertThat(CorrectionAiPayloadParser.extractJsonSpan("no json")).isEqualTo("no json");
    }
}
