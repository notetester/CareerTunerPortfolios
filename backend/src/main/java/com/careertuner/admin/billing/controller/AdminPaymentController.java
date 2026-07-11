package com.careertuner.admin.billing.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.dto.AdminPaymentRow;
import com.careertuner.billing.dto.AdminPaymentSummary;
import com.careertuner.billing.service.BillingService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 결제 조회. 권한은 SecurityConfig 의 /api/admin/** = ADMIN 정책으로 보호된다. */
@RestController
@RequestMapping("/api/admin/payments")
@RequireAdminPermission({"BILLING_READ", "BILLING_ADMIN"})
@RequiredArgsConstructor
public class AdminPaymentController {

    private final BillingService billingService;

    @GetMapping
    public ApiResponse<List<AdminPaymentRow>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(billingService.getAllPayments(status));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminPaymentSummary> summary() {
        return ApiResponse.ok(billingService.getPaymentSummary());
    }
}
