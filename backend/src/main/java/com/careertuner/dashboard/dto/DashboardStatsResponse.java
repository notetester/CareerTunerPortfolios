package com.careertuner.dashboard.dto;

public record DashboardStatsResponse(
        int activeApplications,
        int newApplicationsThisMonth,
        int totalInterviews,
        int interviewsThisWeek,
        int credit,
        int creditLimit,
        int creditsUsedThisMonth,
        int averageFitScore
) {
}
