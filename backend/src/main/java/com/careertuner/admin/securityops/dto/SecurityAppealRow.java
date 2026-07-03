package com.careertuner.admin.securityops.dto;

import java.time.LocalDateTime;

public record SecurityAppealRow(
        Long id,
        String publicRequestId,
        String subjectType,
        String subjectValue,
        Long blockRuleId,
        String submitterEmail,
        String status,
        String reason,
        String decisionReason,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
