package com.careertuner.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaApprovalRequest(
        @NotBlank String challengeToken,
        Boolean approve,
        String deviceName
) {
}
