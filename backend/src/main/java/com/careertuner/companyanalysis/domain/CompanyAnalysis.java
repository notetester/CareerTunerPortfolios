package com.careertuner.companyanalysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyAnalysis {

    private Long id;
    private Long applicationCaseId;
    private Long jobPostingId;
    private Integer jobPostingRevision;
    private String companySummary;
    private String recentIssues;
    private String industry;
    private String competitors;
    private String interviewPoints;
    private String sources;
    private String verifiedFacts;
    private String aiInferences;
    private String sourceType;
    private LocalDateTime checkedAt;
    private LocalDateTime refreshRecommendedAt;
    private LocalDateTime confirmedAt;
    private String adminMemo;
    // 모델 선택·실행 provenance (지원건별 모델 선택·재실행 슬라이스). 전부 NULL 허용 —
    // 기존 행·자동 파이프라인 생성분은 NULL(=unknown). strict 재분석만 채운다.
    private String requestedProvider;
    private String actualProvider;
    private String actualModel;
    private Boolean fallbackUsed;
    private String attemptPath;
    private String runMode;
    private LocalDateTime createdAt;
}
