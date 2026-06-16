package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserBenefitBalance {

    private Long id;
    private Long userId;
    private String benefitCode;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private int grantedQuantity;
    private int usedQuantity;
    private int remainingQuantity;
    private String sourcePlanCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
