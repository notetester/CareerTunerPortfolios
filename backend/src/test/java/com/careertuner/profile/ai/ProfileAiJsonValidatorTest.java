package com.careertuner.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.profile.domain.UserProfile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ProfileAiJsonValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProfileScoreCalculator scoreCalculator = new ProfileScoreCalculator();
    private final ProfileAiJsonValidator validator = new ProfileAiJsonValidator(
            scoreCalculator,
            new ProfileQualityGuard(scoreCalculator));
    private final JobFamilyWeightPolicy policy = new JobFamilyWeightPolicy();

    @Test
    void calculatesServerSideWeightedScoreFromValidJson() throws Exception {
        JsonNode payload = objectMapper.readTree(validPayload(80));

        ProfileAiResult result = validator.validate(
                "PROFILE_SUMMARY",
                UserProfile.builder()
                        .desiredJob("사무 운영")
                        .desiredIndustry("교육 서비스")
                        .skills("[\"Excel\",\"문서 작성\",\"고객 응대\"]")
                        .career("[{\"role\":\"운영 보조\",\"tasks\":\"월별 보고서와 고객 문의를 정리했습니다.\",\"achievements\":\"보고서 12건을 작성하고 문의 처리 시간을 20% 줄였습니다.\"}]")
                        .projects("[{\"title\":\"업무 문서 정리\",\"result\":\"반복 문의 30건을 유형별로 분류했습니다.\"}]")
                        .resumeText("운영 보조로 월별 보고서 12건을 작성하고 고객 문의 처리 시간을 20% 줄였습니다.")
                        .selfIntro("문서 작성과 고객 응대 경험을 바탕으로 운영 업무를 체계적으로 지원했습니다.")
                        .build(),
                JobFamily.GENERAL,
                policy.weightsFor(JobFamily.GENERAL),
                payload,
                new CareerAnalysisAiUsage("gpt-5", 100, 50, 150, false));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.completenessScore()).isEqualTo(80);
        assertThat(result.aiScore()).isEqualTo(80);
        assertThat(result.qualityPenalty()).isZero();
        assertThat(result.qualityWarnings()).isEmpty();
        assertThat(result.criteria()).hasSize(ScoreCriterion.values().length);
        assertThat(result.usage().totalTokens()).isEqualTo(150);
    }

    @Test
    void rejectsOutOfRangeAiScore() throws Exception {
        JsonNode payload = objectMapper.readTree(validPayload(101));

        assertThatThrownBy(() -> validator.validate(
                "PROFILE_SUMMARY",
                UserProfile.builder().desiredJob("사무").build(),
                JobFamily.GENERAL,
                policy.weightsFor(JobFamily.GENERAL),
                payload,
                CareerAnalysisAiUsage.mockUsage()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0~100");
    }

    @Test
    void appliesServerQualityPenaltyEvenWhenAiReturnsHighScore() throws Exception {
        JsonNode payload = objectMapper.readTree(validPayload(90));

        ProfileAiResult result = validator.validate(
                "PROFILE_SUMMARY",
                UserProfile.builder()
                        .desiredJob("간호사")
                        .desiredIndustry("병원")
                        .skills("[\"React\",\"Spring Boot\",\"SQL\"]")
                        .career("[{\"tasks\":\"ㅋㅋㅋㅋㅋㅋ\"}]")
                        .resumeText("테스트 테스트 테스트")
                        .selfIntro("ㅎㅎㅎㅎㅎ")
                        .build(),
                JobFamily.HEALTHCARE_SERVICE,
                policy.weightsFor(JobFamily.HEALTHCARE_SERVICE),
                payload,
                new CareerAnalysisAiUsage("qwen3-profile-lora-v3", 100, 50, 150, false));

        assertThat(result.completenessScore()).isLessThan(90);
        assertThat(result.aiScore()).isEqualTo(90);
        assertThat(result.qualityPenalty()).isPositive();
        assertThat(result.qualityWarnings()).isNotEmpty();
        assertThat(result.qualityRecommendations()).isNotEmpty();
        assertThat(result.criteria())
                .filteredOn(row -> row.criterion() == ScoreCriterion.JOB_SKILL_ALIGNMENT)
                .singleElement()
                .satisfies(row -> assertThat(row.rawScore()).isLessThan(90));
        assertThat(result.summary()).contains("서버 품질 검증");
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
