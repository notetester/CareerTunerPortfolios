package com.careertuner.admin.securityops.dto;

import jakarta.validation.constraints.NotBlank;

public record SecurityAppealPolicyRequest(
        @NotBlank String displayName,
        Boolean enabled,
        Boolean allowMultipleOpen,
        Integer maxOpenPerSubject,
        Integer submitterDailyLimit,
        Integer tokenTtlHours,
        String configJson,
        String reason) {
}
