package com.careertuner.admin.dashboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.dashboard.dto.AdminDashboardOverviewResponse;
import com.careertuner.admin.dashboard.mapper.AdminDashboardMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final AdminDashboardMapper adminDashboardMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardOverviewResponse getOverview(boolean includeUserMetrics, boolean includeAiMetrics) {
        return new AdminDashboardOverviewResponse(
                includeUserMetrics ? adminDashboardMapper.countUsers() : 0,
                includeUserMetrics ? adminDashboardMapper.countActiveUsers() : 0,
                includeUserMetrics ? adminDashboardMapper.countApplications() : 0,
                includeAiMetrics ? adminDashboardMapper.countFitAnalyses() : 0,
                includeAiMetrics ? adminDashboardMapper.countInterviewSessions() : 0,
                includeAiMetrics ? adminDashboardMapper.countAiCallsThisMonth() : 0,
                includeAiMetrics ? adminDashboardMapper.countReviewRequiredAnalyses() : 0);
    }
}
