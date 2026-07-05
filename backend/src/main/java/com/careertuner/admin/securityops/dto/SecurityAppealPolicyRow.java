package com.careertuner.admin.securityops.dto;

import java.time.LocalDateTime;

public record SecurityAppealPolicyRow(
        Long id,
        String policyCode,
        String displayName,
        boolean enabled,
        boolean allowMultipleOpen,
        int maxOpenPerSubject,
        int submitterDailyLimit,
        int tokenTtlHours,
        String configJson,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
