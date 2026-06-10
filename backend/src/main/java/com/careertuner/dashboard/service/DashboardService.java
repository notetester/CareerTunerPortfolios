package com.careertuner.dashboard.service;

import com.careertuner.dashboard.dto.DashboardSummaryResponse;

public interface DashboardService {

    /** 조회용. 저장된 요약을 재사용하고, 입력이 바뀐 경우에만 1회 자동 재생성한다(크레딧 미차감). */
    DashboardSummaryResponse getSummary(Long userId);

    /** 사용자가 명시적으로 요청한 재생성. 항상 AI를 실행하고 크레딧을 차감한다. */
    DashboardSummaryResponse refreshSummary(Long userId);
}
