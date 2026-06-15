package com.careertuner.dashboard.dto;

/** 지원 상태별 건수 요약(B 소유 application_case 상태의 읽기 전용 집계). */
public record DashboardStatusCountResponse(
        String status,
        int count
) {
}
