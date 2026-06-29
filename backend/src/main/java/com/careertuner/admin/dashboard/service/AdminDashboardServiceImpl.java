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
    public AdminDashboardOverviewResponse getOverview() {
        return new AdminDashboardOverviewResponse(
                adminDashboardMapper.countUsers(),
                adminDashboardMapper.countActiveUsers(),
                adminDashboardMapper.countApplications(),
                adminDashboardMapper.countFitAnalyses(),
                adminDashboardMapper.countInterviewSessions(),
                adminDashboardMapper.countAiCallsThisMonth(),
                adminDashboardMapper.countReviewRequiredAnalyses());
    }
}
