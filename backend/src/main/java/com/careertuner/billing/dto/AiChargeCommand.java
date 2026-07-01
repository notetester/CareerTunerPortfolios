package com.careertuner.billing.dto;

public record AiChargeCommand(
        Long userId,
        String featureType,
        String refType,
        Long refId,
        Long aiUsageLogId,
        Integer creditCost,
        String reason,
        String policyAcknowledgementKey
) {
}
