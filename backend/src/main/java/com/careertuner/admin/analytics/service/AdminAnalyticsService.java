package com.careertuner.admin.analytics.service;

import java.util.List;

import com.careertuner.admin.analytics.dto.AdminCareerAnalysisRunResponse;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoRequest;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoResponse;
import com.careertuner.admin.analytics.dto.AdminAnalyticsSummaryResponse;

public interface AdminAnalyticsService {

    AdminAnalyticsSummaryResponse getSummary();

    List<AdminCareerAnalysisRunResponse> listRuns(Long userId);

    // 실행 이력 운영 메모 (career_analysis_run 단위)
    List<AdminCareerRunMemoResponse> listMemos(Long runId);

    AdminCareerRunMemoResponse createMemo(Long runId, Long adminUserId, AdminCareerRunMemoRequest request);

    AdminCareerRunMemoResponse updateMemo(Long runId, Long memoId, AdminCareerRunMemoRequest request);

    void deleteMemo(Long runId, Long memoId);
}
