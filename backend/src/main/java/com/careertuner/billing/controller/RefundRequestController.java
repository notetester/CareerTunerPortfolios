package com.careertuner.billing.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.dto.RefundRequestCreateRequest;
import com.careertuner.billing.dto.RefundRequestResponse;
import com.careertuner.billing.service.RefundRequestService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/billing/refunds")
@RequiredArgsConstructor
public class RefundRequestController {
    private final RefundRequestService service;

    @GetMapping
    public ApiResponse<List<RefundRequestResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.listMine(authUser.id()));
    }

    @PostMapping
    public ApiResponse<RefundRequestResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody RefundRequestCreateRequest request) {
        return ApiResponse.ok(service.create(authUser.id(), request));
    }
}
