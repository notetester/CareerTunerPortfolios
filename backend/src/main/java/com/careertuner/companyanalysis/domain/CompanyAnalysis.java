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
    private LocalDateTime createdAt;
}
