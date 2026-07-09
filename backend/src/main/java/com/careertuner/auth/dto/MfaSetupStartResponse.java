package com.careertuner.auth.dto;

public record MfaSetupStartResponse(
        String secret,
        String otpauthUri,
        String deviceName
) {
}
