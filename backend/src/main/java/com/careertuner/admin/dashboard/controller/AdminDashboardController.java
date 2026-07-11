package com.careertuner.admin.dashboard.controller;

import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.admin.permission.service.EffectivePermissionService;
import com.careertuner.admin.dashboard.dto.AdminDashboardOverviewResponse;
import com.careertuner.admin.dashboard.service.AdminDashboardService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequireAdminPermission({"USER_READ", "AI_READ"})
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final EffectivePermissionService effectivePermissionService;

    @GetMapping("/overview")
    public ApiResponse<AdminDashboardOverviewResponse> overview(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        boolean superAdmin = "SUPER_ADMIN".equals(authUser.role());
        Set<String> permissions = superAdmin
                ? Set.of()
                : effectivePermissionService.getEffectivePermissions(authUser.id());
        return ApiResponse.ok(adminDashboardService.getOverview(
                superAdmin || permissions.contains("USER_READ"),
                superAdmin || permissions.contains("AI_READ")));
    }
}
