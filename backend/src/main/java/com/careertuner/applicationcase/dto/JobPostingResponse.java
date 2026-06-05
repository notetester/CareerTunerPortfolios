package com.careertuner.applicationcase.dto;

import java.time.LocalDateTime;

import com.careertuner.applicationcase.domain.JobPosting;

public record JobPostingResponse(
        Long id,
        Long applicationCaseId,
        String originalText,
        String uploadedFileUrl,
        String extractedText,
        String sourceType,
        LocalDateTime createdAt
) {
    public static JobPostingResponse from(JobPosting jobPosting) {
        return new JobPostingResponse(
                jobPosting.getId(),
                jobPosting.getApplicationCaseId(),
                jobPosting.getOriginalText(),
                jobPosting.getUploadedFileUrl(),
                jobPosting.getExtractedText(),
                jobPosting.getSourceType(),
                jobPosting.getCreatedAt());
    }
}
