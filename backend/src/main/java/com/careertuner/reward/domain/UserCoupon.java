package com.careertuner.reward.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 사용자에게 발급된 쿠폰(user_coupon). status: ISSUED/USED/EXPIRED. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon {
    private Long id;
    private Long couponId;
    private Long userId;
    private String code;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private Long orderRef;
    private LocalDateTime createdAt;
}
