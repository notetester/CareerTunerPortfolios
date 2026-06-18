package com.careertuner.billing.dto;

import com.careertuner.billing.domain.SubscriptionBenefitPolicy;

public record SubscriptionBenefitPolicyResponse(
        String planCode,
        String benefitCode,
        String benefitName,
        String benefitType,
        int quantity,
        String resetCycle,
        String overagePolicy,
        int creditCost,
        int sortOrder
) {
    public static SubscriptionBenefitPolicyResponse from(SubscriptionBenefitPolicy policy) {
        return new SubscriptionBenefitPolicyResponse(
                policy.getPlanCode(),
                policy.getBenefitCode(),
                policy.getBenefitName(),
                policy.getBenefitType(),
                policy.getQuantity(),
                policy.getResetCycle(),
                policy.getOveragePolicy(),
                policy.getCreditCost(),
                policy.getSortOrder());
    }
}
