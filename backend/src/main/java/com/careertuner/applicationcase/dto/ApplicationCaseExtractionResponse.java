package com.careertuner.applicationcase.dto;

import java.time.LocalDateTime;

import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;

public record ApplicationCaseExtractionResponse(
        Long id,
        Long applicationCaseId,
        Long jobPostingId,
        String sourceType,
        String status,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ApplicationCaseExtractionResponse from(ApplicationCaseExtraction extraction) {
        if (extraction == null) {
            return null;
        }
        return new ApplicationCaseExtractionResponse(
                extraction.getId(),
                extraction.getApplicationCaseId(),
                extraction.getJobPostingId(),
                extraction.getSourceType(),
                extraction.getStatus(),
                extraction.getErrorMessage(),
                extraction.getStartedAt(),
                extraction.getFinishedAt(),
                extraction.getCreatedAt(),
                extraction.getUpdatedAt());
    }
}
