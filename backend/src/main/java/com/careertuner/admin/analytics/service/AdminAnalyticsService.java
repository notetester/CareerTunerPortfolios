package com.careertuner.admin.analytics.service;

import java.util.List;

import com.careertuner.admin.analytics.dto.AdminAnalysisFailureResponse;
import com.careertuner.admin.analytics.dto.AdminCareerAnalysisRunResponse;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoRequest;
import com.careertuner.admin.analytics.dto.AdminCareerRunMemoResponse;
import com.careertuner.admin.analytics.dto.AdminAnalyticsSummaryResponse;
import com.careertuner.admin.analytics.dto.AdminQualityFlagResponse;

public interface AdminAnalyticsService {

    AdminAnalyticsSummaryResponse getSummary();

    /** 분석 실패 큐: 비정상(FAILED/FALLBACK) 분석 결과 목록. */
    List<AdminAnalysisFailureResponse> listFailures();

    /** 품질 검수 큐: 최신 적합도 분석에 결정적 휴리스틱을 적용한 점검 항목. */
    List<AdminQualityFlagResponse> listQualityFlags();

    List<AdminCareerAnalysisRunResponse> listRuns(Long userId);

    // 실행 이력 운영 메모 (career_analysis_run 단위)
    List<AdminCareerRunMemoResponse> listMemos(Long runId);

    AdminCareerRunMemoResponse createMemo(Long runId, Long adminUserId, AdminCareerRunMemoRequest request);

    AdminCareerRunMemoResponse updateMemo(Long runId, Long memoId, AdminCareerRunMemoRequest request);

    void deleteMemo(Long runId, Long memoId);
}
