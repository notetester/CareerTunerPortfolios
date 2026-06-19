package com.careertuner.billing.dto;

import java.time.LocalDateTime;

/** 사용자 결제/구독 요약. 구독이 없으면 FREE 로 본다. */
public record MyBillingResponse(
        String currentPlanCode,
        String currentPlanName,
        String subscriptionStatus,
        LocalDateTime periodEnd,
        int creditBalance
) {}
