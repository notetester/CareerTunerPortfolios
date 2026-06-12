package com.careertuner.admin.analytics.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAnalysisSource {

    private Long applicationCaseId;
    private Long fitAnalysisId;
    private String userName;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private String missingSkills;
    private Integer fitScore;
    private LocalDateTime analyzedAt;

    // 품질 검수 휴리스틱용 추가 필드(fit_analysis 읽기 전용).
    private String matchedSkills;
    private String certificateRecommendations;
    private String strategy;
    private String status;
    private String conditionMatrix;
    private String analysisConfidence;
    private String applyDecision;
}
