package com.careertuner.reward.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 쿠폰 정의(coupon). discountType: CREDIT/PERCENT/AMOUNT. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {
    private Long id;
    private String code;
    private String name;
    private String discountType;
    private int discountValue;
    private int minPurchase;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Integer maxIssue;
    private int issuedCount;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
