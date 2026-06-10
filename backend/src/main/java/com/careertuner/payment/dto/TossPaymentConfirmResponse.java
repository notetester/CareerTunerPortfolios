package com.careertuner.payment.dto;

public record TossPaymentConfirmResponse(
        String orderId,
        String paymentKey,
        int amount,
        int creditAmount,
        String status,
        int balance
) {
}
