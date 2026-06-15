package com.careertuner.home.dto;

import java.util.List;

import com.careertuner.dashboard.dto.DashboardActivityResponse;
import com.careertuner.dashboard.dto.DashboardApplicationResponse;
import com.careertuner.dashboard.dto.DashboardFocusResponse;
import com.careertuner.dashboard.dto.DashboardTodoResponse;
import com.careertuner.dashboard.dto.DashboardUserResponse;

/**
 * 로그인 홈 요약(C 담당). 핵심 상태·최근 지원 건·다음 액션·최근 활동을 한 번에 보여준다.
 *
 * <p>대시보드 집계를 재사용해 가볍게 투영하므로 dashboard 도메인(동일 담당 C)의 응답 타입을 그대로 쓴다.
 */
public record HomeSummaryResponse(
        DashboardUserResponse user,
        DashboardFocusResponse focus,
        String aiSummary,
        List<DashboardApplicationResponse> recentApplications,
        List<DashboardTodoResponse> nextActions,
        List<DashboardActivityResponse> recentActivities,
        // 시작 준비(온보딩) 진행률: 핵심 흐름 단계별 완료 여부.
        List<HomeOnboardingStepResponse> onboardingSteps
) {
}
