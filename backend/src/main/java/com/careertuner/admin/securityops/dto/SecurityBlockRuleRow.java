package com.careertuner.admin.securityops.dto;

import java.time.LocalDateTime;

public record SecurityBlockRuleRow(
        Long id,
        String ruleType,
        String ruleValue,
        String scope,
        String actionType,
        String category,
        String reason,
        String memo,
        boolean active,
        boolean wafSyncEnabled,
        String wafSyncStatus,
        String wafRuleId,
        LocalDateTime lastSyncedAt,
        LocalDateTime expiresAt,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
