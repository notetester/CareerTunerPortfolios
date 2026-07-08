package com.careertuner.auth.dto;

public record MfaDisableRequest(
        String code,
        String backupCode
) {
}
