package com.careertuner.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 구독 요금제(subscription_plan). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    private Long id;
    private String code;
    private String name;
    private Integer monthlyPrice;
    private Integer yearlyPrice;
    private String description;
    private boolean active;
    private Integer sortOrder;
}
