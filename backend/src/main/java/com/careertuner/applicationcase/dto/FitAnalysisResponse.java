package com.careertuner.applicationcase.dto;

import java.time.LocalDateTime;

import com.careertuner.applicationcase.domain.FitAnalysis;

public record FitAnalysisResponse(
        Long id,
        Long applicationCaseId,
        Integer fitScore,
        String matchedSkills,
        String missingSkills,
        String recommendedStudy,
        String recommendedCertificates,
        String strategy,
        LocalDateTime createdAt
) {
    public static FitAnalysisResponse from(FitAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return new FitAnalysisResponse(
                analysis.getId(),
                analysis.getApplicationCaseId(),
                analysis.getFitScore(),
                analysis.getMatchedSkills(),
                analysis.getMissingSkills(),
                analysis.getRecommendedStudy(),
                analysis.getRecommendedCertificates(),
                analysis.getStrategy(),
                analysis.getCreatedAt());
    }
}
