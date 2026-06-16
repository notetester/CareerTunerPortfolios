package com.careertuner.dashboard.dto;

/** 준비도 게이지 구성 요소(0~100). */
public record DashboardReadinessComponentResponse(
        String key,
        String label,
        int score,
        String description
) {
}
