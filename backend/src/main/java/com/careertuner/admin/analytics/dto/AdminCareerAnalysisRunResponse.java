package com.careertuner.admin.analytics.dto;

import java.time.LocalDateTime;

import com.careertuner.admin.analytics.domain.AdminCareerAnalysisRun;

public record AdminCareerAnalysisRunResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String analysisType,
        String status,
        String inputSnapshot,
        String result,
        String model,
        int tokenUsage,
        String errorMessage,
        boolean retryable,
        LocalDateTime createdAt
) {
    public static AdminCareerAnalysisRunResponse from(AdminCareerAnalysisRun run) {
        return new AdminCareerAnalysisRunResponse(
                run.getId(),
                run.getUserId(),
                run.getUserName(),
                run.getUserEmail(),
                run.getAnalysisType(),
                run.getStatus(),
                run.getInputSnapshot(),
                run.getResult(),
                run.getModel(),
                run.getTokenUsage(),
                run.getErrorMessage(),
                run.isRetryable(),
                run.getCreatedAt());
    }
}
