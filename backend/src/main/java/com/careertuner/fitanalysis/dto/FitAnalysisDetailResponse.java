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
        String conditionMatrix,
        String analysisConfidence,
        String applyDecision,
        List<FitScoreBreakdownResponse> scoreBreakdown,
        FitActionBoardResponse actionBoard,
        List<String> adverseStrategies,
        List<String> next24HourActions,
        List<FitToneStrategyResponse> toneStrategies,
        String model,
        String promptVersion,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        FitAnalysisApplicationResponse application,
        List<FitAnalysisLearningTaskResponse> learningTasks
) {
    public static FitAnalysisDetailResponse of(FitAnalysisResult result,
                                               List<FitAnalysisLearningTaskResponse> learningTasks,
                                               List<FitScoreBreakdownResponse> scoreBreakdown,
                                               FitActionBoardResponse actionBoard,
                                               List<String> adverseStrategies,
                                               List<String> next24HourActions,
                                               List<FitToneStrategyResponse> toneStrategies) {
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
                result.getConditionMatrix(),
                result.getAnalysisConfidence(),
                result.getApplyDecision(),
                scoreBreakdown,
                actionBoard,
                adverseStrategies,
                next24HourActions,
                toneStrategies,
                result.getModel(),
                result.getPromptVersion(),
                result.getStatus(),
                result.getErrorMessage(),
                result.getCreatedAt(),
                FitAnalysisApplicationResponse.from(result),
                learningTasks);
    }
}
