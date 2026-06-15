package com.careertuner.admin.jobanalysis.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminJobAnalysisRow {

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
    private String employmentType;
    private String experienceLevel;
    private String requiredSkills;
    private String preferredSkills;
    private String duties;
    private String qualifications;
    private String difficulty;
    private String summary;
    private String evidence;
    private String ambiguousConditions;
    private LocalDateTime confirmedAt;
    private String adminMemo;
    private LocalDateTime createdAt;
}
