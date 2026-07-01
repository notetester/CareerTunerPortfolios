package com.careertuner.billing.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.dto.CurrentRefundPolicyResponse;
import com.careertuner.billing.dto.RefundPolicyAcknowledgementRequest;
import com.careertuner.billing.service.RefundPolicyService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/billing/refund-policy")
@RequiredArgsConstructor
public class RefundPolicyController {

    private final RefundPolicyService refundPolicyService;

    @GetMapping("/current")
    public ApiResponse<CurrentRefundPolicyResponse> current(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(refundPolicyService.currentForUser(authUser.id()));
    }

    @PostMapping("/acknowledgements")
    public ApiResponse<CurrentRefundPolicyResponse> acknowledge(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody RefundPolicyAcknowledgementRequest request) {
        return ApiResponse.ok(refundPolicyService.acknowledge(
                authUser.id(), request.policyId(), request.triggerType(), request.actionKey()));
    }
}
