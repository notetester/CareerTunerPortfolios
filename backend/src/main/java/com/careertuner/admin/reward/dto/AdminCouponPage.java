package com.careertuner.admin.reward.dto;

import java.util.List;

import com.careertuner.reward.domain.Coupon;

public record AdminCouponPage(
        List<Coupon> items,
        long total,
        int page,
        int size
) {
}
