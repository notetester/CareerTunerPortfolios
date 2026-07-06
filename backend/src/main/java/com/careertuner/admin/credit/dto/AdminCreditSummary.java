package com.careertuner.admin.credit.dto;

public record AdminCreditSummary(
        long totalTransactions,
        long adminAdjustmentCount,
        long totalGranted,
        long totalDeducted,
        long totalUserBalance
) {
}
