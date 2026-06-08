package com.careertuner.admin.fitanalysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFitAnalysisResult {

    private Long id;
    private Long applicationCaseId;
    private Long userId;
    private String userName;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private String applicationStatus;
    private boolean favorite;
    private Integer fitScore;
    private String matchedSkills;
    private String missingSkills;
    private String recommendedStudy;
    private String recommendedCertificates;
    private String strategy;
    private LocalDateTime createdAt;
}
