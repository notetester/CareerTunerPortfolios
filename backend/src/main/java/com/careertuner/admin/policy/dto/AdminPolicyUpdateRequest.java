package com.careertuner.admin.policy.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminPolicyUpdateRequest(
        @NotBlank String configJson,
        @NotBlank String scheduleType,
        Boolean active,
        String reason
) {
}
