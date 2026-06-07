package com.careertuner.admin.analysis.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminCompanyAnalysisRow {

    private Long id;
    private Long applicationCaseId;
    private Long userId;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private String companySummary;
    private String recentIssues;
    private String industry;
    private String competitors;
    private String sources;
    private LocalDateTime createdAt;
}
