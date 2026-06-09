package com.careertuner.dashboard.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.dashboard.dto.DashboardSummaryResponse;
import com.careertuner.dashboard.service.DashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(dashboardService.getSummary(authUser.id()));
    }

    // 사용자가 명시적으로 요청한 대시보드 요약 재생성. AI를 강제로 실행하고 크레딧을 차감한다.
    @PostMapping("/summary/refresh")
    public ApiResponse<DashboardSummaryResponse> refresh(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(dashboardService.refreshSummary(authUser.id()));
    }
}
