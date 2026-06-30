package com.careertuner.payment.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.payment.dto.TossPaymentCancelRequest;
import com.careertuner.payment.dto.TossPaymentCancelResponse;
import com.careertuner.payment.dto.TossPaymentConfirmRequest;
import com.careertuner.payment.dto.TossPaymentConfirmResponse;
import com.careertuner.payment.dto.TossPaymentReadyRequest;
import com.careertuner.payment.dto.TossPaymentReadyResponse;
import com.careertuner.payment.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments/toss")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 크레딧 상품 결제창을 띄우기 전에 서버 기준 결제 대기 건을 만든다. */
    @PostMapping("/ready")
    public ApiResponse<TossPaymentReadyResponse> ready(@AuthenticationPrincipal AuthUser authUser,
                                                       @Valid @RequestBody TossPaymentReadyRequest request) {
        return ApiResponse.ok(paymentService.ready(authUser.id(), authUser.email(), request));
    }

    /** Toss 성공 리다이렉트 이후 결제를 승인하고 사용자 크레딧을 충전한다. */
    @PostMapping("/confirm")
    public ApiResponse<TossPaymentConfirmResponse> confirm(@AuthenticationPrincipal AuthUser authUser,
                                                           @Valid @RequestBody TossPaymentConfirmRequest request) {
        return ApiResponse.ok(paymentService.confirm(authUser.id(), request));
    }

    @PostMapping("/cancel")
    public ApiResponse<TossPaymentCancelResponse> cancel(@AuthenticationPrincipal AuthUser authUser,
                                                         @Valid @RequestBody TossPaymentCancelRequest request) {
        return ApiResponse.ok(paymentService.cancelReadyPayment(authUser.id(), request.orderId()));
    }
}
