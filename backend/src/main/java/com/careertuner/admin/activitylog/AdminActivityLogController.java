package com.careertuner.admin.activitylog;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.activitylog.AdminActivityLogService.ActivityLogPage;
import com.careertuner.admin.activitylog.AdminActivityLogService.SecurityHistoryPage;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 활동 로그 + 보안 이력 조회 API. */
@RestController
@RequestMapping("/api/admin")
@RequireAdminPermission({"ADMIN_AUDIT_READ", "SECURITY_LOG_READ", "AUDIT_ADMIN"})
@RequiredArgsConstructor
public class AdminActivityLogController {

    private final AdminActivityLogService service;

    @GetMapping("/activity-logs")
    public ApiResponse<ActivityLogPage> search(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(service.search(authUser, keyword, domain, activityType, success, userId, from, to, page, size));
    }

    @GetMapping("/security-histories")
    public ApiResponse<SecurityHistoryPage> searchSecurity(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String eventStage,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(service.searchSecurity(authUser, keyword, eventType, eventStage, success, userId, from, to, page, size));
    }
}
