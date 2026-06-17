package com.careertuner.profile.dto;

public record ProfileCriterionScoreResponse(
        String criterion,
        String label,
        int rawScore,
        int weight,
        double weightedScore,
        String evidence,
        String improvement
) {
}
