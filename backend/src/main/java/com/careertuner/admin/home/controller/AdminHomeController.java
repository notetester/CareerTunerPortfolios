package com.careertuner.admin.home.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.admin.home.dto.AdminHomeSummaryResponse;
import com.careertuner.admin.home.service.AdminHomeService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/home")
@RequireAdminPermission({"AI_READ"})
@RequiredArgsConstructor
public class AdminHomeController {

    private final AdminHomeService adminHomeService;

    @GetMapping("/summary")
    public ApiResponse<AdminHomeSummaryResponse> summary(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(adminHomeService.getSummary());
    }
}
