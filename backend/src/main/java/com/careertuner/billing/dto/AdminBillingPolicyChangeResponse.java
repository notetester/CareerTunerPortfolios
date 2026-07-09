package com.careertuner.billing.dto;

import java.time.LocalDateTime;

import com.careertuner.billing.domain.BillingPolicyChange;

public record AdminBillingPolicyChangeResponse(
        Long id,
        String targetType,
        String targetCode,
        String currentSnapshotJson,
        String nextSnapshotJson,
        LocalDateTime effectiveFrom,
        String applyMode,
        String status,
        Long createdBy,
        LocalDateTime createdAt,
        Long canceledBy,
        LocalDateTime canceledAt
) {
    public static AdminBillingPolicyChangeResponse from(BillingPolicyChange change) {
        return new AdminBillingPolicyChangeResponse(
                change.getId(),
                change.getTargetType(),
                change.getTargetCode(),
                change.getCurrentSnapshotJson(),
                change.getNextSnapshotJson(),
                change.getEffectiveFrom(),
                change.getApplyMode(),
                change.getStatus(),
                change.getCreatedBy(),
                change.getCreatedAt(),
                change.getCanceledBy(),
                change.getCanceledAt());
    }
}
