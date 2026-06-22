package com.careertuner.applicationcase.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCaseExtraction {

    private Long id;
    private Long applicationCaseId;
    private Long jobPostingId;
    private Long userId;
    private String sourceType;
    private String status;
    private String errorMessage;
    private String extractionStrategy;
    private Integer qualityScore;
    private String qualityStatus;
    private String qualityReportJson;
    private String modelVersionsJson;
    private boolean fallbackEligible;
    private String fallbackReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
