package com.careertuner.admin.analytics.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.careertuner.admin.analytics.dto.AdminAnalyticsSummaryResponse;
import com.careertuner.admin.analytics.service.AdminAnalyticsService;
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
        if (!"ADMIN".equals(authUser.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
        return ApiResponse.ok(adminAnalyticsService.getSummary());
    }
}
