package com.careertuner.admin.companyanalysis.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminCompanyAnalysisRow {

    private Long id;
    private Long applicationCaseId;
    private Long jobPostingId;
    private Integer jobPostingRevision;
    private Integer latestJobPostingRevision;
    private Boolean staleAgainstLatestPosting;
    private Long userId;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private String companySummary;
    private String recentIssues;
    private String industry;
    private String competitors;
    private String interviewPoints;
    private String sources;
    private String verifiedFacts;
    private String aiInferences;
    /**
     * virtual 필드 — DB 컬럼이 아니라, 저장된 aiInferences 의 {@code kind=UNKNOWN} 마커를
     * 응답 직전 펼친 값이다. 함께 aiInferences 에서는 마커가 제거된다.
     */
    private String unknowns;
    private String sourceType;
    private LocalDateTime checkedAt;
    private LocalDateTime refreshRecommendedAt;
    private LocalDateTime confirmedAt;
    private String adminMemo;
    // 모델 선택·실행 provenance (지원건별 모델 선택·재실행). 자동 초기 실행·strict 재분석만 채우고, 레거시 행은 NULL.
    private String requestedProvider;
    private String actualProvider;
    private String actualModel;
    private Boolean fallbackUsed;
    private String attemptPath;
    private String runMode;
    private LocalDateTime createdAt;
}
