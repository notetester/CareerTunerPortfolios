package com.careertuner.companyanalysis.dto;

import java.time.LocalDateTime;

import com.careertuner.companyanalysis.domain.CompanyAnalysis;

public record CompanyAnalysisResponse(
        Long id,
        Long applicationCaseId,
        Long jobPostingId,
        Integer jobPostingRevision,
        String companySummary,
        String recentIssues,
        String industry,
        String competitors,
        String interviewPoints,
        String sources,
        String verifiedFacts,
        String aiInferences,
        String sourceType,
        LocalDateTime checkedAt,
        LocalDateTime refreshRecommendedAt,
        LocalDateTime confirmedAt,
        String adminMemo,
        LocalDateTime createdAt
) {
    public static CompanyAnalysisResponse from(CompanyAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return new CompanyAnalysisResponse(
                analysis.getId(),
                analysis.getApplicationCaseId(),
                analysis.getJobPostingId(),
                analysis.getJobPostingRevision(),
                analysis.getCompanySummary(),
                analysis.getRecentIssues(),
                analysis.getIndustry(),
                analysis.getCompetitors(),
                analysis.getInterviewPoints(),
                analysis.getSources(),
                analysis.getVerifiedFacts(),
                analysis.getAiInferences(),
                analysis.getSourceType(),
                analysis.getCheckedAt(),
                analysis.getRefreshRecommendedAt(),
                analysis.getConfirmedAt(),
                analysis.getAdminMemo(),
                analysis.getCreatedAt());
    }
}
