package com.careertuner.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record TossPaymentCancelRequest(
        @NotBlank String orderId
) {}
