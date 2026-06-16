package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionPlan {

    private Long id;
    private String code;
    private String name;
    private int monthlyPrice;
    private Integer yearlyPrice;
    private String description;
    private boolean active;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
