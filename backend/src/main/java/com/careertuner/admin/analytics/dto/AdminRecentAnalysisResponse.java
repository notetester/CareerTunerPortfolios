package com.careertuner.admin.analytics.dto;

import java.time.LocalDateTime;

import com.careertuner.admin.analytics.domain.AdminAnalysisSource;

public record AdminRecentAnalysisResponse(
        Long applicationCaseId,
        Long fitAnalysisId,
        String userName,
        String userEmail,
        String companyName,
        String jobTitle,
        Integer fitScore,
        LocalDateTime analyzedAt
) {

    public static AdminRecentAnalysisResponse from(AdminAnalysisSource source) {
        return new AdminRecentAnalysisResponse(
                source.getApplicationCaseId(),
                source.getFitAnalysisId(),
                source.getUserName(),
                source.getUserEmail(),
                source.getCompanyName(),
                source.getJobTitle(),
                source.getFitScore(),
                source.getAnalyzedAt());
    }
}
