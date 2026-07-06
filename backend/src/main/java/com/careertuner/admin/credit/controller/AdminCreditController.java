package com.careertuner.admin.credit.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.credit.dto.AdminCreditAdjustRequest;
import com.careertuner.admin.credit.dto.AdminCreditAdjustResponse;
import com.careertuner.admin.credit.dto.AdminCreditPage;
import com.careertuner.admin.credit.dto.AdminCreditSummary;
import com.careertuner.admin.credit.service.AdminCreditService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/credits")
@RequiredArgsConstructor
@Validated
public class AdminCreditController {

    private final AdminCreditService service;

    @GetMapping
    public ApiResponse<AdminCreditPage> transactions(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.transactions(authUser, keyword, userId, type, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminCreditSummary> summary(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(service.summary(authUser));
    }

    @PostMapping("/adjust")
    public ApiResponse<AdminCreditAdjustResponse> adjust(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AdminCreditAdjustRequest request
    ) {
        return ApiResponse.ok(service.adjust(authUser, request));
    }
}
