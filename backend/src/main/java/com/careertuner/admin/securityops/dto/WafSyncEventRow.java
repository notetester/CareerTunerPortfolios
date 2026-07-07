package com.careertuner.admin.securityops.dto;

import java.time.LocalDateTime;

public record WafSyncEventRow(
        Long id,
        Long blockRuleId,
        String providerCode,
        String operationType,
        String status,
        String requestPayloadJson,
        String responsePayloadJson,
        String errorMessage,
        Long requestedBy,
        LocalDateTime requestedAt,
        LocalDateTime processedAt) {
}
