package com.careertuner.dashboard.dto;

import java.util.List;

import com.careertuner.analysis.dto.CareerAnalysisRunResponse;

public record DashboardSummaryResponse(
        DashboardUserResponse user,
        DashboardStatsResponse stats,
        DashboardFocusResponse focus,
        List<DashboardApplicationResponse> recentApplications,
        List<DashboardTodoResponse> todos,
        List<DashboardActivityResponse> activities,
        List<DashboardSkillGapResponse> skillGaps,
        // 디자인 분석 §6.4 권장 카드: 최근 면접(점수 변화·핵심 개선점). 면접 기록이 없으면 null.
        DashboardRecentInterviewResponse recentInterview,
        // PRODUCT_STRUCTURE 대시보드 항목: 최근 알림(notification 읽기 전용 참조).
        List<DashboardNotificationResponse> recentNotifications,
        // 전체 취업 준비도 게이지와 최근 변화 요약(결정적 집계, AI 미사용).
        DashboardReadinessResponse readiness,
        DashboardChangeResponse recentChange,
        String aiSummary,
        CareerAnalysisRunResponse analysisRun
) {
}
