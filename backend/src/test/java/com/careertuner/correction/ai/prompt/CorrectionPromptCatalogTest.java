package com.careertuner.correction.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrectionPromptCatalogTest {

    @Test
    void paragraphFailureAddsParagraphGuidance() {
        String prompt = CorrectionPromptCatalog.selfRepairPrompt(
                "corrected_text output paragraphs 1 do not preserve source paragraphs 3",
                "{}");

        assertThat(prompt).contains("문단 수를 원문과 정확히 맞추고");
    }

    @Test
    void cjkFailureAddsLanguageGuidance() {
        String prompt = CorrectionPromptCatalog.selfRepairPrompt("CJK_LEAK", "{}");

        assertThat(prompt).contains("중국어·일본어 문자를 제거");
    }
}
