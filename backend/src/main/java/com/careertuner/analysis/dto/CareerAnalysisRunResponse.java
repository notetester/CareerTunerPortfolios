package com.careertuner.analysis.dto;

import java.time.LocalDateTime;

import com.careertuner.analysis.domain.CareerAnalysisRun;

public record CareerAnalysisRunResponse(
        Long id,
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
    public static CareerAnalysisRunResponse from(CareerAnalysisRun run) {
        return new CareerAnalysisRunResponse(
                run.getId(),
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
