package com.careertuner.analysis.domain;

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
public class AnalysisSource {

    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private LocalDate postingDate;
    private String status;
    private boolean favorite;
    private LocalDateTime applicationUpdatedAt;

    private Long fitAnalysisId;
    private Integer fitScore;
    private String matchedSkills;
    private String missingSkills;
    private String recommendedStudy;
    private String recommendedCertificates;
    private String strategy;
    private LocalDateTime analyzedAt;

    private int interviewCount;
    private Integer averageInterviewScore;
    private int interviewAnswerCount;
    private Integer averageInterviewAnswerScore;
}
