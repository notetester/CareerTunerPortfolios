package com.careertuner.auth.dto;

public record MfaLoginStatusResponse(
        String status,
        TokenResponse token
) {
}
