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
    /** 등록 시 사용자가 고른 OCR provider 스냅샷(CLAUDE/OPENAI/SELF_OCR). 미선택=NULL → 기본 자동 체인. 워커가 라우팅에 읽는다. */
    private String ocrRequestedProvider;
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
