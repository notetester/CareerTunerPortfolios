package com.careertuner.reward.dto;

/** 쿠폰 사용 결과. CREDIT 쿠폰이면 creditGranted/balanceAfter 채워짐. */
public record CouponRedeemResult(
        String code,
        String discountType,
        int creditGranted,
        int balanceAfter,
        String message
) {
}
