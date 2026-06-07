package com.careertuner.applicationcase.dto;

import java.time.LocalDateTime;

import com.careertuner.applicationcase.domain.CompanyAnalysis;

public record CompanyAnalysisResponse(
        Long id,
        Long applicationCaseId,
        String companySummary,
        String recentIssues,
        String industry,
        String competitors,
        String interviewPoints,
        String sources,
        LocalDateTime createdAt
) {
    public static CompanyAnalysisResponse from(CompanyAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return new CompanyAnalysisResponse(
                analysis.getId(),
                analysis.getApplicationCaseId(),
                analysis.getCompanySummary(),
                analysis.getRecentIssues(),
                analysis.getIndustry(),
                analysis.getCompetitors(),
                analysis.getInterviewPoints(),
                analysis.getSources(),
                analysis.getCreatedAt());
    }
}
