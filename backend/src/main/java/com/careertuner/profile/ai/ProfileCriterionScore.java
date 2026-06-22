package com.careertuner.profile.ai;

public record ProfileCriterionScore(
        ScoreCriterion criterion,
        int rawScore,
        int weight,
        double weightedScore,
        String evidence,
        String improvement
) {
}
