package com.careertuner.dashboard.dto;

/**
 * 최근 변화 요약. 재분석이 있었던 지원 건의 최신-직전 점수 차이를 집계한다.
 * 재분석 이력이 없으면 averageScoreDelta 는 null 이다.
 */
public record DashboardChangeResponse(
        int reanalyzedApplications,
        int improvedApplications,
        int declinedApplications,
        Integer averageScoreDelta
) {
}
