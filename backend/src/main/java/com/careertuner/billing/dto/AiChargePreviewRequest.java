package com.careertuner.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record AiChargePreviewRequest(
        @NotBlank String featureType,
        Integer creditCost,
        @NotBlank String actionKey
) {
}
