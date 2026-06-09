package com.careertuner.admin.dashboard.dto;

/**
 * 관리자 운영 종합 대시보드 응답(C 담당).
 *
 * <p>분석에 한정된 admin/analytics 와 달리, 운영자가 로그인 직후 보는 도메인 횡단 현황 카운트를 담는다.
 * 모두 읽기 전용 집계이며 다른 도메인 데이터를 수정하지 않는다.
 */
public record AdminDashboardOverviewResponse(
        int totalUsers,
        int activeUsers,
        int totalApplications,
        int totalFitAnalyses,
        int totalInterviewSessions,
        int aiCallsThisMonth
) {
}
