package com.careertuner.admin.billing.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.dto.AdminRefundPolicySaveRequest;
import com.careertuner.billing.dto.RefundPolicyResponse;
import com.careertuner.billing.service.RefundPolicyService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.common.web.SitesFinancialMutation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/refund-policies")
@RequireAdminPermission({"BILLING_READ"})
@RequiredArgsConstructor
public class AdminRefundPolicyController {

    private final RefundPolicyService refundPolicyService;

    @GetMapping
    public ApiResponse<List<RefundPolicyResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(refundPolicyService.list(authUser));
    }

    @SitesFinancialMutation
    @PutMapping("/draft")
    @RequireAdminPermission({"BILLING_UPDATE"})
    public ApiResponse<RefundPolicyResponse> saveDraft(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AdminRefundPolicySaveRequest request) {
        return ApiResponse.ok(refundPolicyService.saveDraft(authUser, request));
    }

    @SitesFinancialMutation
    @PostMapping("/{id}/publish")
    @RequireAdminPermission({"BILLING_UPDATE"})
    public ApiResponse<RefundPolicyResponse> publish(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(refundPolicyService.publish(authUser, id));
    }
}
