package com.careertuner.admin.permission.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.permission.annotation.AdminRoleOnly;
import com.careertuner.admin.permission.dto.AdminMePermissionsResponse;
import com.careertuner.admin.permission.dto.AdminNotificationOptOutUpdateRequest;
import com.careertuner.admin.permission.service.AdminNotificationOptOutService;
import com.careertuner.admin.permission.service.EffectivePermissionService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 본인 컨텍스트 API.
 *
 * <ul>
 *   <li>GET /api/admin/me/permissions — 실효 권한 목록(프런트 사이드바/메뉴 노출 제어용).
 *       서버 인터셉터 검증이 항상 최종 방어선이며 이 응답은 표시 제어일 뿐이다.</li>
 *   <li>GET/PATCH /api/admin/me/notification-categories — 관리자 알림 카테고리 opt-out 토글.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/me")
@AdminRoleOnly
@RequiredArgsConstructor
public class AdminMeController {

    private final EffectivePermissionService effectivePermissionService;
    private final AdminNotificationOptOutService optOutService;

    @GetMapping("/permissions")
    public ApiResponse<AdminMePermissionsResponse> myPermissions(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        boolean superAdmin = "SUPER_ADMIN".equals(authUser.role());
        List<String> permissions = List.copyOf(
                effectivePermissionService.getEffectivePermissions(authUser.id()));
        return ApiResponse.ok(new AdminMePermissionsResponse(authUser.role(), superAdmin, permissions));
    }

    @GetMapping("/notification-categories")
    public ApiResponse<Map<String, Boolean>> myNotificationCategories(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(optOutService.getAdminCategories(authUser.id()));
    }

    @PatchMapping("/notification-categories")
    public ApiResponse<Map<String, Boolean>> updateNotificationCategory(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AdminNotificationOptOutUpdateRequest request) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(optOutService.updateAdminCategory(authUser.id(), request.type(), request.enabled()));
    }
}
