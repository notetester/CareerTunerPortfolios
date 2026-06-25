package com.careertuner.admin.policy.dto;

import java.time.LocalDateTime;

public record AdminPolicyRunResult(
        String policyCode,
        String status,
        String message,
        LocalDateTime runAt
) {
}
