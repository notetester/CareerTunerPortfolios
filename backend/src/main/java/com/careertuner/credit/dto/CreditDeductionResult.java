package com.careertuner.credit.dto;

/** AI 사용량 로그 기반 크레딧 차감 처리 결과. */
public record CreditDeductionResult(
        Long aiUsageLogId,
        Long userId,
        boolean deducted,
        int creditUsed,
        int balanceAfter,
        String reason
) {

    public static CreditDeductionResult skipped(Long aiUsageLogId,
                                                Long userId,
                                                int creditUsed,
                                                int balanceAfter,
                                                String reason) {
        return new CreditDeductionResult(aiUsageLogId, userId, false, creditUsed, balanceAfter, reason);
    }

    public static CreditDeductionResult deducted(Long aiUsageLogId,
                                                 Long userId,
                                                 int creditUsed,
                                                 int balanceAfter) {
        return new CreditDeductionResult(aiUsageLogId, userId, true, creditUsed, balanceAfter, "DEDUCTED");
    }
}
