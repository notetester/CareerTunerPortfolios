package com.careertuner.admin.dashboard.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.permission.annotation.AdminRoleOnly;
import com.careertuner.admin.dashboard.dto.AdminDashboardOverviewResponse;
import com.careertuner.admin.dashboard.service.AdminDashboardService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/dashboard")
@AdminRoleOnly
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/overview")
    public ApiResponse<AdminDashboardOverviewResponse> overview(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(adminDashboardService.getOverview());
    }
}
