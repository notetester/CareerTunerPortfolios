package com.careertuner.payment.dto;

public record TossPaymentReadyResponse(
        String orderId,
        String productType,
        String productCode,
        String planCode,
        String orderName,
        int amount,
        int creditAmount,
        String customerEmail,
        String successUrl,
        String failUrl
) {
}
