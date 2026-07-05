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
    private LocalDateTime createdAt;
}
