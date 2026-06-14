package com.careertuner.admin.fitanalysis.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;

public record AdminFitAnalysisListItemResponse(
        Long id,
        Long applicationCaseId,
        String userName,
        String userEmail,
        String companyName,
        String jobTitle,
        String applicationStatus,
        boolean favorite,
        Integer fitScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        String model,
        String promptVersion,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        int memoCount,
        LocalDateTime latestMemoAt,
        // 재분석 필요(REANALYSIS) 운영 메모 보유 여부 — 관리자 재분석 요청 상태 필터용.
        boolean reanalysisRequested
) {

    public static AdminFitAnalysisListItemResponse of(AdminFitAnalysisResult result,
                                                      List<String> matchedSkills,
                                                      List<String> missingSkills) {
        return new AdminFitAnalysisListItemResponse(
                result.getId(),
                result.getApplicationCaseId(),
                result.getUserName(),
                result.getUserEmail(),
                result.getCompanyName(),
                result.getJobTitle(),
                result.getApplicationStatus(),
                result.isFavorite(),
                result.getFitScore(),
                matchedSkills,
                missingSkills,
                result.getModel(),
                result.getPromptVersion(),
                result.getStatus(),
                result.getErrorMessage(),
                result.getCreatedAt(),
                result.getMemoCount(),
                result.getLatestMemoAt(),
                result.isReanalysisRequested());
    }
}
