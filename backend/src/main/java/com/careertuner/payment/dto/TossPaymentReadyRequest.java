package com.careertuner.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record TossPaymentReadyRequest(
        @NotBlank String productCode
) {
}
