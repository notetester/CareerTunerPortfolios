package com.careertuner.admin.billing.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.dto.AdminBillingPolicyChangeRequest;
import com.careertuner.billing.dto.AdminBillingPolicyChangeResponse;
import com.careertuner.billing.dto.AdminPlansResponse;
import com.careertuner.billing.dto.AiFeatureBenefitPolicyResponse;
import com.careertuner.billing.dto.SubscriptionBenefitPolicyResponse;
import com.careertuner.billing.service.BillingPolicyChangeService;
import com.careertuner.billing.service.BillingPolicyService;
import com.careertuner.billing.service.BillingService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.common.web.SitesFinancialMutation;

import lombok.RequiredArgsConstructor;

/** 관리자 요금제/크레딧 상품 조회. */
@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {

    private final BillingService billingService;
    private final BillingPolicyService billingPolicyService;
    private final BillingPolicyChangeService policyChangeService;

    @GetMapping
    public ApiResponse<AdminPlansResponse> plans() {
        return ApiResponse.ok(new AdminPlansResponse(
                billingService.getPlans(),
                billingService.getCreditProducts(),
                billingPolicyService.activeBenefitPolicies().stream()
                        .map(SubscriptionBenefitPolicyResponse::from)
                        .toList(),
                billingPolicyService.activeFeatureBenefitPolicies().stream()
                        .map(AiFeatureBenefitPolicyResponse::from)
                        .toList()));
    }

    @GetMapping("/policy-changes")
    public ApiResponse<List<AdminBillingPolicyChangeResponse>> policyChanges(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(policyChangeService.list(authUser));
    }

    @SitesFinancialMutation
    @PostMapping("/policy-changes")
    public ApiResponse<AdminBillingPolicyChangeResponse> createPolicyChange(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AdminBillingPolicyChangeRequest request) {
        return ApiResponse.ok(policyChangeService.create(authUser, request));
    }

    @SitesFinancialMutation
    @PostMapping("/policy-changes/{id}/cancel")
    public ApiResponse<AdminBillingPolicyChangeResponse> cancelPolicyChange(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(policyChangeService.cancel(authUser, id));
    }
}
