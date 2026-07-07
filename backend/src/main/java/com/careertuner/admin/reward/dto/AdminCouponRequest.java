package com.careertuner.admin.reward.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 쿠폰 생성/수정(관리자). discountType: CREDIT/PERCENT/AMOUNT. */
public record AdminCouponRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20) String discountType,
        @NotNull @PositiveOrZero Integer discountValue,
        @NotNull @PositiveOrZero Integer minPurchase,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        @PositiveOrZero Integer maxIssue,
        @NotNull Boolean enabled
) {
}
