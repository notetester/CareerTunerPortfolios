package com.careertuner.billing.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.dto.AiFeatureBenefitPolicyResponse;
import com.careertuner.billing.dto.BenefitTransactionResponse;
import com.careertuner.billing.dto.MyBenefitsResponse;
import com.careertuner.billing.dto.SubscriptionPlanResponse;
import com.careertuner.billing.service.BillingService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/plans")
    public ApiResponse<List<SubscriptionPlanResponse>> plans() {
        return ApiResponse.ok(billingService.listPlans());
    }

    @GetMapping("/feature-benefit-policies")
    public ApiResponse<List<AiFeatureBenefitPolicyResponse>> featureBenefitPolicies() {
        return ApiResponse.ok(billingService.listFeatureBenefitPolicies());
    }

    @GetMapping("/benefits/me")
    public ApiResponse<MyBenefitsResponse> myBenefits(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(billingService.myBenefits(authUser.id()));
    }

    @GetMapping("/benefit-transactions/me")
    public ApiResponse<List<BenefitTransactionResponse>> myBenefitTransactions(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(billingService.myBenefitTransactions(authUser.id(), limit));
    }
}
