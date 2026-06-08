package com.careertuner.fitanalysis.dto;

import java.time.LocalDateTime;

import com.careertuner.fitanalysis.domain.FitAnalysisResult;

public record FitAnalysisDetailResponse(
        Long id,
        Long applicationCaseId,
        Integer fitScore,
        String matchedSkills,
        String missingSkills,
        String recommendedStudy,
        String recommendedCertificates,
        String strategy,
        LocalDateTime createdAt,
        FitAnalysisApplicationResponse application
) {
    public static FitAnalysisDetailResponse from(FitAnalysisResult result) {
        return new FitAnalysisDetailResponse(
                result.getId(),
                result.getApplicationCaseId(),
                result.getFitScore(),
                result.getMatchedSkills(),
                result.getMissingSkills(),
                result.getRecommendedStudy(),
                result.getRecommendedCertificates(),
                result.getStrategy(),
                result.getCreatedAt(),
                FitAnalysisApplicationResponse.from(result));
    }
}
