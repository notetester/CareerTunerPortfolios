package com.careertuner.admin.analytics.service;

import java.util.List;

import com.careertuner.admin.analytics.dto.AdminCareerAnalysisRunResponse;
import com.careertuner.admin.analytics.dto.AdminAnalyticsSummaryResponse;

public interface AdminAnalyticsService {

    AdminAnalyticsSummaryResponse getSummary();

    List<AdminCareerAnalysisRunResponse> listRuns(Long userId);
}
