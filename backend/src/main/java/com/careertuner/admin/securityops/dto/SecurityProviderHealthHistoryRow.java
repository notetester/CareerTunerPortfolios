package com.careertuner.admin.securityops.dto;

import java.time.LocalDateTime;

public record SecurityProviderHealthHistoryRow(
        Long id,
        Long providerConfigId,
        String providerCode,
        String providerType,
        String checkSource,
        String statusBefore,
        String statusAfter,
        String detailMessage,
        Long actorUserId,
        String actorEmail,
        LocalDateTime checkedAt) {
}
