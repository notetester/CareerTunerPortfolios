package com.careertuner.admin.reward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 적립 규칙 값 편집(관리자). */
public record AdminRewardRuleUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @PositiveOrZero Integer pointAmount,
        @NotNull @PositiveOrZero Integer creditAmount,
        @PositiveOrZero Integer dailyCap,
        @NotNull Boolean enabled,
        @Size(max = 255) String description,
        @NotNull Integer sortOrder
) {
}
