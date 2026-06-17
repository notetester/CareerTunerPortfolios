package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 크레딧 원장(credit_transaction). 충전(+)·차감(-) 누적과 거래 후 잔액을 기록한다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditTransaction {

    private Long id;
    private Long userId;
    private Long aiUsageLogId;
    private String type;
    private Integer amount;
    private Integer balanceAfter;
    private String featureType;
    private String reason;
    private LocalDateTime createdAt;
}
