package com.careertuner.fitanalysis.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitAnalysisResult {

    private Long id;
    private Long applicationCaseId;
    private Integer fitScore;
    private String matchedSkills;
    private String missingSkills;
    private String recommendedStudy;
    private String recommendedCertificates;
    private String strategy;
    private String sourceSnapshot;
    private String scoreBasis;
    private String gapRecommendations;
    private String certificateRecommendations;
    private String strategyActions;
    private String conditionMatrix;
    private String analysisConfidence;
    private String applyDecision;
    private String certificateEvidence;   // 자격증 근거 snapshot(JSON 문자열, nullable)
    private String model;
    private String promptVersion;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;

    private String companyName;
    private String jobTitle;
    private LocalDate postingDate;
    private LocalDate deadlineDate;
    private String applicationStatus;
    private boolean favorite;
    private LocalDateTime applicationUpdatedAt;
}
