package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionBenefitPolicy {

    private Long id;
    private String planCode;
    private String benefitCode;
    private String benefitName;
    private String benefitType;
    private int quantity;
    private String resetCycle;
    private String overagePolicy;
    private int creditCost;
    private boolean active;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
