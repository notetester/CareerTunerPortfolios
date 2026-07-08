package com.careertuner.auth.dto;

public record MfaPolicyResponse(
        boolean requireAdmins,
        boolean allowBackupCode,
        boolean allowPushApproval
) {
}
