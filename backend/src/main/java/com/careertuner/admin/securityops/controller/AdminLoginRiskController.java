package com.careertuner.admin.securityops.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.loginrisk.domain.LoginRiskPolicy;
import com.careertuner.loginrisk.dto.LoginRiskPolicyRequest;
import com.careertuner.loginrisk.service.LoginRiskPolicyService;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 위험도(브루트포스) 잠금 정책 관리자 콘솔 API.
 *
 * <p>OFF(무제약) ↔ ON(연속 실패 N회 시 M분 잠금) 토글 + 임계/잠금시간 편집. 집행은 로그인
 * 경로(AuthServiceImpl)가 이 정책을 참조한다. TripTogether login-risk 탐지 임계 편집 축 이식.</p>
 */
@RestController
@RequestMapping("/api/admin/security/login-risk-policy")
@RequireAdminPermission({"SECURITY_READ"})
@RequiredArgsConstructor
public class AdminLoginRiskController {

    private final LoginRiskPolicyService service;
    private final AdminActionLogService actionLogService;

    @GetMapping
    public ApiResponse<LoginRiskPolicy> get(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(service.getCurrent());
    }

    @PatchMapping
    @RequireAdminPermission({"SECURITY_UPDATE"})
    public ApiResponse<LoginRiskPolicy> update(@AuthenticationPrincipal AuthUser authUser,
                                               @RequestBody LoginRiskPolicyRequest request) {
        AdminAccess.requireAdmin(authUser);
        LoginRiskPolicy before = service.getCurrent();

        boolean enabled = request.enabled() != null ? request.enabled() : before.enabled();
        int maxFailedCount = request.maxFailedCount() != null
                ? requireRange("maxFailedCount", request.maxFailedCount(), 1, 1000)
                : before.maxFailedCount();
        int lockMinutes = request.lockMinutes() != null
                ? requireRange("lockMinutes", request.lockMinutes(), 1, 525600)
                : before.lockMinutes();

        LoginRiskPolicy after = service.update(enabled, maxFailedCount, lockMinutes, authUser.id());
        actionLogService.record(authUser, null, "LOGIN_RISK_POLICY_UPDATED", "LOGIN_RISK_POLICY",
                snapshot(before), snapshot(after), request.reason());
        return ApiResponse.ok(after);
    }

    private static int requireRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + "는 " + min + " ~ " + max + " 범위여야 합니다: " + value);
        }
        return value;
    }

    private static String snapshot(LoginRiskPolicy p) {
        return "{\"enabled\":" + p.enabled()
                + ",\"maxFailedCount\":" + p.maxFailedCount()
                + ",\"lockMinutes\":" + p.lockMinutes() + "}";
    }
}
