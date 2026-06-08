package com.careertuner.dashboard.dto;

import java.util.List;

public record DashboardSummaryResponse(
        DashboardUserResponse user,
        DashboardStatsResponse stats,
        DashboardFocusResponse focus,
        List<DashboardApplicationResponse> recentApplications,
        List<DashboardTodoResponse> todos,
        List<DashboardActivityResponse> activities,
        List<DashboardSkillGapResponse> skillGaps,
        String aiSummary
) {
}
