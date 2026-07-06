package com.careertuner.admin.reward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 레벨 정책 생성/수정(관리자). */
public record AdminLevelPolicyRequest(
        @NotNull @Positive Integer level,
        @NotBlank @Size(max = 50) String levelName,
        @NotNull @PositiveOrZero Integer minPoint,
        @NotNull @PositiveOrZero Integer levelupCredit,
        @Size(max = 50) String levelupCouponCode,
        @Size(max = 255) String benefitNote,
        @NotNull Boolean active
) {
}
