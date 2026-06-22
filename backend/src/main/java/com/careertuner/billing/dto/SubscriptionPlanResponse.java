package com.careertuner.billing.dto;

import java.util.List;

import com.careertuner.billing.domain.SubscriptionPlan;

public record SubscriptionPlanResponse(
        String code,
        String name,
        int monthlyPrice,
        Integer yearlyPrice,
        String description,
        int sortOrder,
        List<SubscriptionBenefitPolicyResponse> benefits
) {
    public static SubscriptionPlanResponse of(SubscriptionPlan plan,
                                              List<SubscriptionBenefitPolicyResponse> benefits) {
        return new SubscriptionPlanResponse(
                plan.getCode(),
                plan.getName(),
                plan.getMonthlyPrice(),
                plan.getYearlyPrice(),
                plan.getDescription(),
                plan.getSortOrder(),
                benefits);
    }
}
