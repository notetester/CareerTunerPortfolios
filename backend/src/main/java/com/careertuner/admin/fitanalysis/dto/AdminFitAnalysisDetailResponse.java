package com.careertuner.admin.fitanalysis.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;
import com.careertuner.fitanalysis.dto.FitAnalysisLearningTaskResponse;
import com.careertuner.fitanalysis.dto.FitSafetyResponse;

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
        // 실제 gate reason 목록(상세 화면에서 '왜 검토 필요인지' 판단용). 축약(개인정보·원문 제외).
        List<FitSafetyResponse.Reason> gateReasons,
        // gate review workflow 처리 상태(PENDING/RESOLVED/REANALYSIS_REQUESTED)와 처리 이력.
        String gateReviewStatus,
        LocalDateTime gateReviewedAt,
        String gateReviewerName,
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
                                                    List<FitSafetyResponse.Reason> gateReasons,
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
                gateReasons,
                result.getGateReviewStatus(),
                result.getGateReviewedAt(),
                result.getGateReviewerName(),
                learningTasks,
                memos);
    }
}
