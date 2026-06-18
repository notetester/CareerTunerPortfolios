package com.careertuner.billing.dto;

import com.careertuner.billing.domain.AiFeatureBenefitPolicy;

public record AiFeatureBenefitPolicyResponse(
        String featureType,
        String benefitCode,
        String chargeUnit,
        boolean includedInTicket,
        int defaultCreditCost
) {
    public static AiFeatureBenefitPolicyResponse from(AiFeatureBenefitPolicy policy) {
        return new AiFeatureBenefitPolicyResponse(
                policy.getFeatureType(),
                policy.getBenefitCode(),
                policy.getChargeUnit(),
                policy.isIncludedInTicket(),
                policy.getDefaultCreditCost());
    }
}
