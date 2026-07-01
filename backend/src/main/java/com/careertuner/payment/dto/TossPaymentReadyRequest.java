package com.careertuner.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record TossPaymentReadyRequest(
        String productType,
        @NotBlank String productCode,
        Long refundPolicyId,
        String policyAcknowledgementKey
) {
    public TossPaymentReadyRequest(String productCode) {
        this("CREDIT", productCode, null, null);
    }

    public TossPaymentReadyRequest(String productType, String productCode) {
        this(productType, productCode, null, null);
    }

    public TossPaymentReadyRequest {
        productType = productType == null || productType.isBlank() ? "CREDIT" : productType.trim().toUpperCase();
        productCode = productCode == null ? null : productCode.trim().toUpperCase();
    }
}
