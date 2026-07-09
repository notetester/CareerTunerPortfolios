package com.careertuner.billing.dto;

import com.careertuner.billing.domain.AiFeatureBenefitPolicy;

public record AiFeatureBenefitPolicyResponse(
        String featureType,
        String benefitCode,
        String chargeUnit,
        boolean includedInTicket,
        int defaultCreditCost,
        int minCreditCost,
        int maxCreditCost,
        int creditUnitTokens,
        boolean active
) {
    public static AiFeatureBenefitPolicyResponse from(AiFeatureBenefitPolicy policy) {
        return new AiFeatureBenefitPolicyResponse(
                policy.getFeatureType(),
                policy.getBenefitCode(),
                policy.getChargeUnit(),
                policy.isIncludedInTicket(),
                policy.getDefaultCreditCost(),
                policy.getMinCreditCost(),
                policy.getMaxCreditCost(),
                policy.getCreditUnitTokens(),
                policy.isActive());
    }
}
