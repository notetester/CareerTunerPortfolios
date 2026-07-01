package com.careertuner.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundReviewRequest(
        @NotBlank @Size(max = 1000) String reviewedReason) {
}
