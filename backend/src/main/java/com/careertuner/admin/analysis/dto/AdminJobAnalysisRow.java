package com.careertuner.admin.analysis.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminJobAnalysisRow {

    private Long id;
    private Long applicationCaseId;
    private Long userId;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private String employmentType;
    private String experienceLevel;
    private String requiredSkills;
    private String preferredSkills;
    private String difficulty;
    private String summary;
    private LocalDateTime createdAt;
}
