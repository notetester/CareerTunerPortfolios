package com.careertuner.admin.policy.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.admin.policy.dto.AdminPolicyRunResult;
import com.careertuner.admin.policy.dto.AdminPolicyUpdateRequest;
import com.careertuner.admin.policy.dto.AdminSystemPolicyRow;
import com.careertuner.admin.policy.service.AdminPolicyService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 운영 정책 콘솔. 읽기와 변경 권한을 분리해 집행한다. */
@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
@RequireAdminPermission({"POLICY_READ"})
public class AdminPolicyController {

    private final AdminPolicyService service;

    @GetMapping
    public ApiResponse<List<AdminSystemPolicyRow>> policies(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.policies(authUser));
    }

    @PatchMapping("/{policyCode}")
    @RequireAdminPermission({"POLICY_UPDATE"})
    public ApiResponse<AdminSystemPolicyRow> update(@AuthenticationPrincipal AuthUser authUser,
                                                    @PathVariable String policyCode,
                                                    @Valid @RequestBody AdminPolicyUpdateRequest request) {
        return ApiResponse.ok(service.update(authUser, policyCode, request));
    }

    @PostMapping("/{policyCode}/run")
    @RequireAdminPermission({"POLICY_UPDATE"})
    public ApiResponse<AdminPolicyRunResult> run(@AuthenticationPrincipal AuthUser authUser,
                                                 @PathVariable String policyCode,
                                                 @RequestBody(required = false) AdminPolicyUpdateRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok(service.run(authUser, policyCode, reason));
    }
}
