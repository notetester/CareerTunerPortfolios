package com.careertuner.auth.dto;

public record MfaPolicyUpdateRequest(
        Boolean requireAdmins,
        Boolean allowBackupCode,
        Boolean allowPushApproval
) {
}
