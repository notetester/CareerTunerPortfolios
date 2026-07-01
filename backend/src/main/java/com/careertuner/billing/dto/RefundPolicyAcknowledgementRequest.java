package com.careertuner.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefundPolicyAcknowledgementRequest(
        @NotNull Long policyId,
        @NotBlank String triggerType,
        String actionKey
) {
}
