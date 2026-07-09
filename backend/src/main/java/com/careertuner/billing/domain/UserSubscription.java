package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 사용자 구독(user_subscription). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscription {

    private Long id;
    private Long paymentId;
    private Long userId;
    private String planCode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private String policySnapshotJson;
    private LocalDateTime canceledAt;
}
