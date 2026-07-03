package com.careertuner.admin.securityops.dto;

import jakarta.validation.constraints.NotBlank;

public record SecurityAppealDecisionRequest(
        @NotBlank String status,
        String decisionReason) {
}
