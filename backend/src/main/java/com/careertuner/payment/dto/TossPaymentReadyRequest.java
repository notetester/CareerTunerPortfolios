package com.careertuner.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record TossPaymentReadyRequest(
        String productType,
        @NotBlank String productCode
) {
    public TossPaymentReadyRequest(String productCode) {
        this("CREDIT", productCode);
    }

    public TossPaymentReadyRequest {
        productType = productType == null || productType.isBlank() ? "CREDIT" : productType.trim().toUpperCase();
        productCode = productCode == null ? null : productCode.trim().toUpperCase();
    }
}
