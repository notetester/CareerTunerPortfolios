package com.careertuner.admin.fitanalysis.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;
import com.careertuner.fitanalysis.dto.FitAnalysisLearningTaskResponse;

public record AdminFitAnalysisDetailResponse(
        Long id,
        Long applicationCaseId,
        Long userId,
        String userName,
        String userEmail,
        String companyName,
        String jobTitle,
        String applicationStatus,
        boolean favorite,
        Integer fitScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> recommendedStudy,
        List<String> recommendedCertificates,
        String strategy,
        String sourceSnapshot,
        List<String> scoreBasis,
        String gapRecommendations,
        String certificateRecommendations,
        List<String> strategyActions,
        String conditionMatrix,
        String analysisConfidence,
        String applyDecision,
        String model,
        String promptVersion,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        // review-first evidence gate(R3). R3 이전 분석은 gateStatus=null.
        String gateStatus,
        boolean needsHumanReview,
        int gateReasonCount,
        String gateMaxSeverity,
        String evidenceGateVersion,
        List<FitAnalysisLearningTaskResponse> learningTasks,
        List<AdminFitAnalysisMemoResponse> memos
) {

    public static AdminFitAnalysisDetailResponse of(AdminFitAnalysisResult result,
                                                    List<String> matchedSkills,
                                                    List<String> missingSkills,
                                                    List<String> recommendedStudy,
                                                    List<String> recommendedCertificates,
                                                    List<String> scoreBasis,
                                                    List<String> strategyActions,
                                                    List<FitAnalysisLearningTaskResponse> learningTasks,
                                                    List<AdminFitAnalysisMemoResponse> memos) {
        return new AdminFitAnalysisDetailResponse(
                result.getId(),
                result.getApplicationCaseId(),
                result.getUserId(),
                result.getUserName(),
                result.getUserEmail(),
                result.getCompanyName(),
                result.getJobTitle(),
                result.getApplicationStatus(),
                result.isFavorite(),
                result.getFitScore(),
                matchedSkills,
                missingSkills,
                recommendedStudy,
                recommendedCertificates,
                result.getStrategy(),
                result.getSourceSnapshot(),
                scoreBasis,
                result.getGapRecommendations(),
                result.getCertificateRecommendations(),
                strategyActions,
                result.getConditionMatrix(),
                result.getAnalysisConfidence(),
                result.getApplyDecision(),
                result.getModel(),
                result.getPromptVersion(),
                result.getStatus(),
                result.getErrorMessage(),
                result.getCreatedAt(),
                result.getGateStatus(),
                Boolean.TRUE.equals(result.getGateNeedsHumanReview()),
                result.getGateReasonCount() == null ? 0 : result.getGateReasonCount(),
                result.getGateMaxSeverity(),
                result.getEvidenceGateVersion(),
                learningTasks,
                memos);
    }
}
