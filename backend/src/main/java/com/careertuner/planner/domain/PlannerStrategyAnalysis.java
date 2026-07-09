package com.careertuner.planner.domain;

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
public class PlannerStrategyAnalysis {

    private Long fitAnalysisId;
    private Long latestFitAnalysisId;
    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private LocalDate deadlineDate;
    private Integer fitScore;
    private String strategy;
    private String recommendedStudy;
    private String recommendedCertificates;
    private String strategyActions;
    private String gapRecommendations;
    private String certificateRecommendations;
    private LocalDateTime analysisCreatedAt;
    private LocalDateTime applicationUpdatedAt;
}
