package com.careertuner.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefundRequestCreateRequest(
        @NotNull Long paymentId,
        @NotBlank @Size(max = 40) String reasonCode,
        @Size(max = 1000) String reasonText) {
}
