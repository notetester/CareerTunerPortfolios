package com.careertuner.fitanalysis.dto;

import java.time.LocalDateTime;
import java.util.List;

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
        String sourceSnapshot,
        String scoreBasis,
        String gapRecommendations,
        String certificateRecommendations,
        String strategyActions,
        String model,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        FitAnalysisApplicationResponse application,
        List<FitAnalysisLearningTaskResponse> learningTasks
) {
    public static FitAnalysisDetailResponse of(FitAnalysisResult result,
                                               List<FitAnalysisLearningTaskResponse> learningTasks) {
        return new FitAnalysisDetailResponse(
                result.getId(),
                result.getApplicationCaseId(),
                result.getFitScore(),
                result.getMatchedSkills(),
                result.getMissingSkills(),
                result.getRecommendedStudy(),
                result.getRecommendedCertificates(),
                result.getStrategy(),
                result.getSourceSnapshot(),
                result.getScoreBasis(),
                result.getGapRecommendations(),
                result.getCertificateRecommendations(),
                result.getStrategyActions(),
                result.getModel(),
                result.getStatus(),
                result.getErrorMessage(),
                result.getCreatedAt(),
                FitAnalysisApplicationResponse.from(result),
                learningTasks);
    }
}
