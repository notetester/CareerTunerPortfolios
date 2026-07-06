package com.careertuner.reward.dto;

import java.time.LocalDateTime;

public record CouponItem(
        Long id,
        String code,
        String name,
        String discountType,
        int discountValue,
        int minPurchase,
        String status,
        LocalDateTime issuedAt,
        LocalDateTime usedAt
) {
}
