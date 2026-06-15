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
    private String sourceType;
    private LocalDateTime checkedAt;
    private LocalDateTime refreshRecommendedAt;
    private LocalDateTime confirmedAt;
    private String adminMemo;
    private LocalDateTime createdAt;
}
