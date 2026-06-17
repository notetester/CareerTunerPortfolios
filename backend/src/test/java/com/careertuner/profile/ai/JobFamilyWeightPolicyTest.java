package com.careertuner.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobFamilyWeightPolicyTest {

    private final JobFamilyWeightPolicy policy = new JobFamilyWeightPolicy();

    @Test
    void everyJobFamilyWeightSumIsOneHundred() {
        for (JobFamily family : JobFamily.values()) {
            int sum = policy.weightsFor(family).values().stream().mapToInt(Integer::intValue).sum();

            assertThat(sum).as(family.name()).isEqualTo(100);
        }
    }

    @Test
    void marketingWeightsAchievementMoreThanDocumentConsistency() {
        assertThat(policy.weightsFor(JobFamily.SALES_MARKETING).get(ScoreCriterion.ACHIEVEMENT_EVIDENCE))
                .isGreaterThan(policy.weightsFor(JobFamily.SALES_MARKETING).get(ScoreCriterion.DOCUMENT_CONSISTENCY));
    }
}
