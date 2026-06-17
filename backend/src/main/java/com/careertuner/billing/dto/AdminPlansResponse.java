package com.careertuner.billing.dto;

import java.util.List;

import com.careertuner.billing.domain.CreditProduct;
import com.careertuner.billing.domain.SubscriptionPlan;

/** 관리자 요금제/크레딧 상품 관리 화면용 묶음 응답. */
public record AdminPlansResponse(
        List<SubscriptionPlan> plans,
        List<CreditProduct> creditProducts
) {}
