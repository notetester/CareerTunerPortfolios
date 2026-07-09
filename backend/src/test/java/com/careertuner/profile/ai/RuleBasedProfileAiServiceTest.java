package com.careertuner.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.careertuner.profile.domain.UserProfile;

import tools.jackson.databind.ObjectMapper;

class RuleBasedProfileAiServiceTest {

    private final ProfileScoreCalculator scoreCalculator = new ProfileScoreCalculator();
    private final RuleBasedProfileAiService service = new RuleBasedProfileAiService(
            new JobFamilyWeightPolicy(),
            scoreCalculator,
            new ProfileQualityGuard(scoreCalculator),
            new ObjectMapper());

    @Test
    void evaluatesProfileWithJobFamilyWeights() {
        UserProfile profile = UserProfile.builder()
                .desiredJob("마케팅 AE")
                .desiredIndustry("광고 대행사")
                .skills("[\"마케팅\",\"SNS 운영\",\"GA4\",\"Excel\"]")
                .projects("[{\"title\":\"SNS 캠페인\",\"result\":\"팔로워 20% 증가\"}]")
                .resumeText("SNS 캠페인을 운영해 클릭률을 12% 개선했습니다.")
                .selfIntro("고객 문제를 분석하고 콘텐츠 방향을 제안하는 일을 선호합니다.")
                .portfolioLinks("[\"https://example.com\"]")
                .build();

        ProfileAiResult result = service.evaluate(profile, "PROFILE_COMPLETENESS");

        assertThat(result.jobFamily()).isEqualTo(JobFamily.SALES_MARKETING);
        assertThat(result.completenessScore()).isBetween(1, 100);
        assertThat(result.criteria()).hasSize(ScoreCriterion.values().length);
        assertThat(result.extractedSkills()).contains("마케팅", "SNS 운영", "GA4", "Excel");
        assertThat(result.usage().model()).isEqualTo("profile-rule-v2");
    }

    @Test
    void penalizesMeaninglessFilledProfile() {
        UserProfile profile = UserProfile.builder()
                .desiredJob("간호사")
                .desiredIndustry("병원")
                .skills("[\"ㅋㅋㅋㅋㅋㅋ\",\"테스트\"]")
                .career("[{\"company\":\"테스트\",\"tasks\":\"ㅋㅋㅋㅋㅋㅋ\",\"achievements\":\"없음\"}]")
                .projects("[{\"title\":\"아무거나\",\"description\":\"ㅎㅎㅎㅎㅎ\"}]")
                .resumeText("테스트 테스트 테스트")
                .selfIntro("ㅋㅋㅋㅋㅋㅋ")
                .build();

        ProfileAiResult result = service.evaluate(profile, "PROFILE_COMPLETENESS");

        assertThat(result.completenessScore()).isLessThan(60);
        assertThat(result.gaps()).anyMatch(gap -> gap.contains("분석하기 어려운") || gap.contains("의미"));
        assertThat(result.criteria())
                .filteredOn(row -> row.criterion() == ScoreCriterion.EXPERIENCE_SPECIFICITY)
                .singleElement()
                .satisfies(row -> assertThat(row.rawScore()).isLessThan(70));
    }
}
