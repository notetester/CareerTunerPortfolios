package com.careertuner.billing.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.domain.CreditProduct;
import com.careertuner.billing.domain.CreditTransaction;
import com.careertuner.billing.domain.Payment;
import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.dto.CreditPurchaseRequest;
import com.careertuner.billing.dto.MyBillingResponse;
import com.careertuner.billing.dto.SubscribeRequest;
import com.careertuner.billing.dto.UsageRow;
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
    public ApiResponse<List<SubscriptionPlan>> plans() {
        return ApiResponse.ok(billingService.getPlans());
    }

    @GetMapping("/credit-products")
    public ApiResponse<List<CreditProduct>> creditProducts() {
        return ApiResponse.ok(billingService.getCreditProducts());
    }

    @GetMapping("/me")
    public ApiResponse<MyBillingResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(billingService.getMyBilling(authUser.id()));
    }

    @GetMapping("/payments")
    public ApiResponse<List<Payment>> payments(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(billingService.getMyPayments(authUser.id()));
    }

    @GetMapping("/usage")
    public ApiResponse<List<UsageRow>> usage(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(billingService.getMonthlyUsage(authUser.id()));
    }

    @GetMapping("/credit-transactions")
    public ApiResponse<List<CreditTransaction>> creditTransactions(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(billingService.getMyCreditTransactions(authUser.id()));
    }

    @PostMapping("/subscribe")
    public ApiResponse<MyBillingResponse> subscribe(@AuthenticationPrincipal AuthUser authUser,
                                                    @Validated @RequestBody SubscribeRequest request) {
        return ApiResponse.ok(billingService.subscribe(authUser.id(), request.planCode(), request.cycle()));
    }

    @PostMapping("/credits/purchase")
    public ApiResponse<MyBillingResponse> purchaseCredits(@AuthenticationPrincipal AuthUser authUser,
                                                          @Validated @RequestBody CreditPurchaseRequest request) {
        return ApiResponse.ok(billingService.purchaseCredits(authUser.id(), request.productCode()));
    }

    @PostMapping("/subscription/cancel")
    public ApiResponse<MyBillingResponse> cancelSubscription(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(billingService.cancelSubscription(authUser.id()));
    }
}
