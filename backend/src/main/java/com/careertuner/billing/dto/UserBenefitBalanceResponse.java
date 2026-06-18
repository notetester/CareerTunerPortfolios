package com.careertuner.billing.dto;

import java.time.LocalDateTime;

import com.careertuner.billing.domain.UserBenefitBalance;

public record UserBenefitBalanceResponse(
        String benefitCode,
        String benefitName,
        int grantedQuantity,
        int usedQuantity,
        int remainingQuantity,
        String sourcePlanCode,
        LocalDateTime periodStart,
        LocalDateTime periodEnd
) {
    public static UserBenefitBalanceResponse of(UserBenefitBalance balance, String benefitName) {
        return new UserBenefitBalanceResponse(
                balance.getBenefitCode(),
                benefitName,
                balance.getGrantedQuantity(),
                balance.getUsedQuantity(),
                balance.getRemainingQuantity(),
                balance.getSourcePlanCode(),
                balance.getPeriodStart(),
                balance.getPeriodEnd());
    }
}
