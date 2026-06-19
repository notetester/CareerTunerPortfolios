package com.careertuner.billing.dto;

public record BenefitConsumeResult(
        boolean consumed,
        String reason,
        String benefitCode,
        int remainingQuantity
) {
    public static BenefitConsumeResult consumed(String benefitCode, int remainingQuantity) {
        return new BenefitConsumeResult(true, "CONSUMED", benefitCode, remainingQuantity);
    }

    public static BenefitConsumeResult skipped(String benefitCode, int remainingQuantity, String reason) {
        return new BenefitConsumeResult(false, reason, benefitCode, remainingQuantity);
    }
}
