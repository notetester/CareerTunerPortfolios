package com.careertuner.billing.dto;

import java.time.LocalDateTime;

import com.careertuner.billing.domain.BenefitTransaction;

public record BenefitTransactionResponse(
        Long id,
        String benefitCode,
        String transactionType,
        int amount,
        int balanceAfter,
        String refType,
        Long refId,
        Long aiUsageLogId,
        String reason,
        LocalDateTime createdAt
) {
    public static BenefitTransactionResponse from(BenefitTransaction transaction) {
        return new BenefitTransactionResponse(
                transaction.getId(),
                transaction.getBenefitCode(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getRefType(),
                transaction.getRefId(),
                transaction.getAiUsageLogId(),
                transaction.getReason(),
                transaction.getCreatedAt());
    }
}
