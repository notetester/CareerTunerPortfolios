package com.careertuner.auth.dto;

public record MfaStatusResponse(
        boolean enabled,
        boolean verified,
        String mfaType,
        String deviceName,
        boolean pushEnabled,
        int backupCodeRemaining,
        boolean adminSetupRecommended
) {
}
