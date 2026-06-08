package com.careertuner.dashboard.service;

import com.careertuner.dashboard.dto.DashboardSummaryResponse;

public interface DashboardService {

    DashboardSummaryResponse getSummary(Long userId);
}
