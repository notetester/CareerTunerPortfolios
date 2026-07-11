package com.careertuner.admin.billing.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.dto.RefundRequestResponse;
import com.careertuner.billing.dto.RefundReviewRequest;
import com.careertuner.billing.service.RefundRequestService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.common.web.SitesFinancialMutation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/refunds")
@RequireAdminPermission({"BILLING_READ"})
@RequiredArgsConstructor
public class AdminRefundRequestController {
    private final RefundRequestService service;

    @GetMapping
    public ApiResponse<List<RefundRequestResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listAdmin(authUser, status));
    }

    @SitesFinancialMutation
    @PostMapping("/{id}/approve")
    @RequireAdminPermission({"BILLING_UPDATE"})
    public ApiResponse<RefundRequestResponse> approve(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody RefundReviewRequest request) {
        return ApiResponse.ok(service.approve(authUser, id, request));
    }

    @SitesFinancialMutation
    @PostMapping("/{id}/reject")
    @RequireAdminPermission({"BILLING_UPDATE"})
    public ApiResponse<RefundRequestResponse> reject(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody RefundReviewRequest request) {
        return ApiResponse.ok(service.reject(authUser, id, request));
    }
}
