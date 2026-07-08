package com.careertuner.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaLoginVerifyRequest(
        @NotBlank String challengeToken,
        String code,
        String backupCode,
        Boolean useApprovedChallenge
) {
}
