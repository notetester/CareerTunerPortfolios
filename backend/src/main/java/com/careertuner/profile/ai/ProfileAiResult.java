package com.careertuner.profile.ai;

import java.util.List;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

public record ProfileAiResult(
        String featureType,
        String summary,
        List<String> extractedSkills,
        List<String> strengths,
        List<String> gaps,
        List<String> recommendations,
        int completenessScore,
        JobFamily jobFamily,
        List<ProfileCriterionScore> criteria,
        CareerAnalysisAiUsage usage,
        String status,
        String errorMessage,
        int aiScore,
        int qualityPenalty,
        List<String> qualityWarnings,
        List<String> qualityRecommendations
) {
    public ProfileAiResult(
            String featureType,
            String summary,
            List<String> extractedSkills,
            List<String> strengths,
            List<String> gaps,
            List<String> recommendations,
            int completenessScore,
            JobFamily jobFamily,
            List<ProfileCriterionScore> criteria,
            CareerAnalysisAiUsage usage,
            String status,
            String errorMessage
    ) {
        this(
                featureType,
                summary,
                extractedSkills,
                strengths,
                gaps,
                recommendations,
                completenessScore,
                jobFamily,
                criteria,
                usage,
                status,
                errorMessage,
                completenessScore,
                0,
                List.of(),
                List.of());
    }
}
