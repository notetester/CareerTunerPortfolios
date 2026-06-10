package com.careertuner.companyanalysis.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Size;

public record CompanyAnalysisReviewRequest(
        String companySummary,
        String recentIssues,
        @Size(max = 100) String industry,
        String competitors,
        String interviewPoints,
        String sources,
        String verifiedFacts,
        String aiInferences,
        @Size(max = 30) String sourceType,
        LocalDateTime checkedAt,
        LocalDateTime refreshRecommendedAt,
        Boolean confirmed
) {
}
