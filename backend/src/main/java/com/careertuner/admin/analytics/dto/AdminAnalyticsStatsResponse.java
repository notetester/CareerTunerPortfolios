package com.careertuner.admin.analytics.dto;

public record AdminAnalyticsStatsResponse(
        int totalUsers,
        int activeUsers,
        int totalApplications,
        int analyzedApplications,
        int totalInterviews,
        int averageFitScore,
        int creditsUsedThisMonth
) {
}
