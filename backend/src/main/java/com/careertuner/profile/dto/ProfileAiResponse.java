package com.careertuner.profile.dto;

import java.util.List;

public record ProfileAiResponse(
        String featureType,
        String summary,
        List<String> extractedSkills,
        List<String> strengths,
        List<String> gaps,
        List<String> recommendations,
        int completenessScore,
        String jobFamily,
        String jobFamilyLabel,
        List<ProfileCriterionScoreResponse> criteria,
        String model,
        String status,
        int aiScore,
        int qualityPenalty,
        List<String> qualityWarnings,
        List<String> qualityRecommendations
) {
}
