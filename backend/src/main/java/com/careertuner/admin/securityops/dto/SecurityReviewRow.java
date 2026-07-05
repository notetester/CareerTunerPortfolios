package com.careertuner.admin.securityops.dto;

import java.time.LocalDateTime;

public record SecurityReviewRow(
        Long id,
        String reviewType,
        String subjectType,
        String subjectValue,
        int riskScore,
        String riskLevel,
        String status,
        String decisionAction,
        String reason,
        String evidenceJson,
        Long createdBy,
        Long assignedTo,
        Long decidedBy,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
