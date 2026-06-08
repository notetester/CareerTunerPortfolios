package com.careertuner.jobposting.dto;

import java.time.LocalDateTime;

import com.careertuner.jobposting.domain.JobPosting;

public record JobPostingResponse(
        Long id,
        Long applicationCaseId,
        Integer revision,
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
                jobPosting.getRevision(),
                jobPosting.getOriginalText(),
                jobPosting.getUploadedFileUrl(),
                jobPosting.getExtractedText(),
                jobPosting.getSourceType(),
                jobPosting.getCreatedAt());
    }
}
