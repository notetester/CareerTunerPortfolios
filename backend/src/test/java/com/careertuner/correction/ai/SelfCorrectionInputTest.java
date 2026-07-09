package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfCorrectionInputTest {

    @Test
    @DisplayName("self-intro constraints preserve at least 85 percent and every source paragraph")
    void defaultConstraints_selfIntroPreservesLengthAndParagraphs() {
        Map<String, Object> constraints = SelfCorrectionInput.defaultConstraints(
                "SELF_INTRO_CORRECTION", "가".repeat(1000));

        assertThat(constraints)
                .containsEntry("min_chars", 850)
                .containsEntry("target_chars", 1000)
                .containsEntry("max_chars", 1100)
                .containsEntry("preserve_paragraphs", true);
    }

    @Test
    @DisplayName("interview and resume constraints use their task-specific minimum ratios")
    void defaultConstraints_usesTaskRatios() {
        assertThat(SelfCorrectionInput.defaultConstraints(
                "INTERVIEW_ANSWER_CORRECTION", "가".repeat(1000)))
                .containsEntry("min_chars", 900)
                .containsEntry("max_chars", 1250)
                .containsEntry("preserve_paragraphs", false);
        assertThat(SelfCorrectionInput.defaultConstraints(
                "RESUME_EXPRESSION_IMPROVEMENT", "가".repeat(1000)))
                .containsEntry("min_chars", 800)
                .containsEntry("max_chars", 1150)
                .containsEntry("preserve_paragraphs", false);
    }

    @Test
    @DisplayName("long inputs keep ordered constraints without silently capping the target")
    void defaultConstraints_longInputKeepsOrderedLengths() {
        assertThat(SelfCorrectionInput.defaultConstraints(
                "SELF_INTRO_CORRECTION", "가".repeat(12000)))
                .containsEntry("min_chars", 10200)
                .containsEntry("target_chars", 12000)
                .containsEntry("max_chars", 13200);
    }
}
