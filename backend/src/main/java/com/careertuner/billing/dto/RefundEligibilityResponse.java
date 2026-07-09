package com.careertuner.billing.dto;

public record RefundEligibilityResponse(
        Long paymentId,
        String eligibilityResult,
        String decisionCode,
        String message,
        boolean creditUsed,
        boolean benefitUsed,
        int refundAmount,
        Long policyId,
        int policyVersion,
        String policyTitle,
        String policySummary,
        int withdrawalDays) {
}
