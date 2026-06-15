package com.careertuner.admin.analytics.dto;

import java.util.List;

public record AdminAnalyticsSummaryResponse(
        AdminAnalyticsStatsResponse stats,
        List<AdminCountResponse> planDistribution,
        List<AdminCountResponse> applicationStatusDistribution,
        List<AdminSkillGapResponse> skillGaps,
        List<AdminFitScoreBandResponse> fitScoreBands,
        List<AdminRecentAnalysisResponse> recentAnalyses,
        List<AdminDailyUsageResponse> dailyUsage,
        List<AdminPromptPerformanceResponse> promptPerformance
) {
}
