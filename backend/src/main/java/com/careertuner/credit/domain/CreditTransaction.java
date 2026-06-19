package com.careertuner.credit.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 크레딧 충전, 차감, 환불, 관리자 조정 내역을 남기는 원장 엔티티. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditTransaction {

    private Long id;
    private Long userId;
    private Long aiUsageLogId;
    private String type;
    private int amount;
    private int balanceAfter;
    private String featureType;
    private String reason;
    private LocalDateTime createdAt;
}
