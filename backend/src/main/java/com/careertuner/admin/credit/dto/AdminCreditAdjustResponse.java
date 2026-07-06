package com.careertuner.admin.credit.dto;

public record AdminCreditAdjustResponse(
        Long transactionId,
        Long userId,
        int amount,
        int balanceBefore,
        int balanceAfter
) {
}
