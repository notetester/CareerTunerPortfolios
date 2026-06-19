package com.careertuner.payment.dto;

public record TossPaymentConfirmResponse(
        String orderId,
        String paymentKey,
        String productType,
        String productCode,
        String planCode,
        int amount,
        int creditAmount,
        String status,
        int balance
) {
}
