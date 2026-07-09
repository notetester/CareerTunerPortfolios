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
        String extractionStrategy,
        Integer qualityScore,
        String qualityStatus,
        String qualityReportJson,
        String modelVersionsJson,
        boolean fallbackEligible,
        String fallbackReason,
        LocalDateTime reviewedAt,
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
                extraction.getExtractionStrategy(),
                extraction.getQualityScore(),
                extraction.getQualityStatus(),
                extraction.getQualityReportJson(),
                extraction.getModelVersionsJson(),
                extraction.isFallbackEligible(),
                extraction.getFallbackReason(),
                extraction.getReviewedAt(),
                extraction.getStartedAt(),
                extraction.getFinishedAt(),
                extraction.getCreatedAt(),
                extraction.getUpdatedAt());
    }
}
