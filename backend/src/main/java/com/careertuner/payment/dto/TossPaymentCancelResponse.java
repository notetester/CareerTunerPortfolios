package com.careertuner.payment.dto;

public record TossPaymentCancelResponse(
        String orderId,
        String productType,
        String productCode,
        String planCode,
        String status
) {}
