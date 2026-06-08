package com.careertuner.companyanalysis.dto;

import jakarta.validation.constraints.Size;

public record CompanyAnalysisReviewRequest(
        String companySummary,
        String recentIssues,
        @Size(max = 100) String industry,
        String competitors,
        String interviewPoints,
        String sources,
        Boolean confirmed
) {
}
