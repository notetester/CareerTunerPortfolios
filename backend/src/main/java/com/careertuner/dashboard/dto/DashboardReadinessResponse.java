package com.careertuner.dashboard.dto;

import java.util.List;

/**
 * 전체 취업 준비도 게이지. 분석 실행률·평균 적합도·학습 완료율·면접 연습률의 가중 평균으로,
 * 모든 입력은 C가 읽기 전용으로 집계한 결정적 값이다(AI 미사용).
 */
public record DashboardReadinessResponse(
        int overall,
        List<DashboardReadinessComponentResponse> components
) {
}
