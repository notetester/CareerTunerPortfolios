package com.careertuner.admin.analytics.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.analytics.dto.AdminAnalyticsSummaryResponse;
import com.careertuner.admin.analytics.dto.AdminCareerAnalysisRunResponse;
import com.careertuner.admin.analytics.service.AdminAnalyticsService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/summary")
    public ApiResponse<AdminAnalyticsSummaryResponse> summary(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.getSummary());
    }

    @GetMapping("/runs")
    public ApiResponse<List<AdminCareerAnalysisRunResponse>> runs(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Long userId) {
        requireAdmin(authUser);
        return ApiResponse.ok(adminAnalyticsService.listRuns(userId));
    }

    private static void requireAdmin(AuthUser authUser) {
        if (!"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}
