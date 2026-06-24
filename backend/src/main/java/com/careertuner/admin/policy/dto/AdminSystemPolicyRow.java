package com.careertuner.admin.policy.dto;

import java.time.LocalDateTime;

public record AdminSystemPolicyRow(
        Long id,
        String policyCode,
        String displayName,
        String description,
        String configJson,
        String scheduleType,
        Boolean active,
        LocalDateTime lastRunAt,
        String lastRunStatus,
        String lastRunMessage,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
