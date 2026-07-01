package com.careertuner.billing.dto;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminRefundPolicySaveRequest(
        @NotBlank String title,
        String summary,
        @NotBlank String content,
        @NotNull Map<String, Object> rules,
        Boolean adverse,
        @NotNull LocalDateTime effectiveAt
) {
}
