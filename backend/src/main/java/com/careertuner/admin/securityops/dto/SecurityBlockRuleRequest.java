package com.careertuner.admin.securityops.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public record SecurityBlockRuleRequest(
        @NotBlank String ruleType,
        @NotBlank String ruleValue,
        String scope,
        String actionType,
        String category,
        String reason,
        String memo,
        Boolean active,
        Boolean wafSyncEnabled,
        LocalDateTime expiresAt) {
}
