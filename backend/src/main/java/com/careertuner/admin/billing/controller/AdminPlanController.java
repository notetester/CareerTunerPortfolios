package com.careertuner.admin.billing.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.dto.AdminPlansResponse;
import com.careertuner.billing.service.BillingService;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 요금제/크레딧 상품 조회. */
@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {

    private final BillingService billingService;

    @GetMapping
    public ApiResponse<AdminPlansResponse> plans() {
        return ApiResponse.ok(new AdminPlansResponse(
                billingService.getPlans(),
                billingService.getCreditProducts()));
    }
}
