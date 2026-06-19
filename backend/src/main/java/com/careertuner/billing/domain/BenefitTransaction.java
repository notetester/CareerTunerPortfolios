package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BenefitTransaction {

    private Long id;
    private Long userId;
    private String benefitCode;
    private String transactionType;
    private int amount;
    private int balanceAfter;
    private String refType;
    private Long refId;
    private Long aiUsageLogId;
    private String reason;
    private LocalDateTime createdAt;
}
