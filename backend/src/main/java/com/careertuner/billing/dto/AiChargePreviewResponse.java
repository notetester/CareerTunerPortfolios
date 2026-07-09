package com.careertuner.billing.dto;

public record AiChargePreviewResponse(
        String featureType,
        String chargeType,
        String benefitCode,
        int chargeAmount,
        int minimumCreditCost,
        int maximumCreditCost,
        int creditUnitTokens,
        boolean usageBased,
        int remainingTicket,
        int currentCredit,
        boolean sufficient,
        String triggerType,
        String actionKey,
        Long refundPolicyId,
        int refundPolicyVersion,
        String refundPolicyTitle,
        String refundPolicySummary,
        String refundPolicyRulesJson
) {
}
