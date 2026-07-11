package com.careertuner.admin.dashboard.service;

import com.careertuner.admin.dashboard.dto.AdminDashboardOverviewResponse;

public interface AdminDashboardService {

    AdminDashboardOverviewResponse getOverview(boolean includeUserMetrics, boolean includeAiMetrics);
}
