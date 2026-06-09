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
    private String model;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;

    private String companyName;
    private String jobTitle;
    private LocalDate postingDate;
    private String applicationStatus;
    private boolean favorite;
    private LocalDateTime applicationUpdatedAt;
}
