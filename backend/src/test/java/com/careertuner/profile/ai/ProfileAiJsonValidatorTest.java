package com.careertuner.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ProfileAiJsonValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProfileAiJsonValidator validator = new ProfileAiJsonValidator(new ProfileScoreCalculator());
    private final JobFamilyWeightPolicy policy = new JobFamilyWeightPolicy();

    @Test
    void calculatesServerSideWeightedScoreFromValidJson() throws Exception {
        JsonNode payload = objectMapper.readTree(validPayload(80));

        ProfileAiResult result = validator.validate(
                "PROFILE_SUMMARY",
                JobFamily.GENERAL,
                policy.weightsFor(JobFamily.GENERAL),
                payload,
                new CareerAnalysisAiUsage("gpt-5", 100, 50, 150, false));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.completenessScore()).isEqualTo(80);
        assertThat(result.criteria()).hasSize(ScoreCriterion.values().length);
        assertThat(result.usage().totalTokens()).isEqualTo(150);
    }

    @Test
    void rejectsOutOfRangeAiScore() throws Exception {
        JsonNode payload = objectMapper.readTree(validPayload(101));

        assertThatThrownBy(() -> validator.validate(
                "PROFILE_SUMMARY",
                JobFamily.GENERAL,
                policy.weightsFor(JobFamily.GENERAL),
                payload,
                CareerAnalysisAiUsage.mockUsage()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0~100");
    }

    private String validPayload(int score) {
        return """
                {
                  "summary": "테스트 요약",
                  "extractedSkills": ["Excel"],
                  "strengths": ["강점"],
                  "gaps": ["보완점"],
                  "recommendations": ["추천"],
                  "criterionScores": %s
                }
                """.formatted(criterionScores(score));
    }

    private String criterionScores(int score) {
        Map<ScoreCriterion, Integer> scores = new EnumMap<>(ScoreCriterion.class);
        for (ScoreCriterion criterion : ScoreCriterion.values()) {
            scores.put(criterion, score);
        }
        return scores.entrySet().stream()
                .map(entry -> """
                        {
                          "criterion": "%s",
                          "rawScore": %d,
                          "evidence": "근거",
                          "improvement": "개선"
                        }
                        """.formatted(entry.getKey().name(), entry.getValue()))
                .toList()
                .toString();
    }
}
