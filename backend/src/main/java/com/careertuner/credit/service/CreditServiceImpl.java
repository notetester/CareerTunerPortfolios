package com.careertuner.credit.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.domain.CreditAiUsageLog;
import com.careertuner.credit.domain.CreditTransaction;
import com.careertuner.credit.dto.CreditDeductionResult;
import com.careertuner.credit.mapper.CreditMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private static final String TRANSACTION_TYPE_AI_USAGE = "AI_USAGE";

    private final CreditMapper creditMapper;

    @Override
    @Transactional
    public CreditDeductionResult deductByAiUsageLog(Long aiUsageLogId) {
        return deduct(aiUsageLogId, null);
    }

    @Override
    @Transactional
    public CreditDeductionResult deductByAiUsageLog(Long aiUsageLogId, int creditUsed) {
        if (creditUsed < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차감 크레딧은 0 이상이어야 합니다.");
        }
        return deduct(aiUsageLogId, creditUsed);
    }

    private CreditDeductionResult deduct(Long aiUsageLogId, Integer requestedCreditUsed) {
        // 사용 로그 행을 먼저 잠가 동일 AI 결과의 동시 정산을 직렬화한다. 두 요청이 모두 잔액을
        // 차감한 뒤 원장 unique key에서 한쪽이 500으로 끝나는 창을 없앤다.
        CreditAiUsageLog usageLog = creditMapper.findAiUsageLogById(aiUsageLogId);
        if (usageLog == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 사용량 로그를 찾을 수 없습니다.");
        }

        Integer currentCredit = creditMapper.findUserCredit(usageLog.getUserId());
        int balanceBefore = currentCredit == null ? 0 : currentCredit;

        if (!"SUCCESS".equals(usageLog.getStatus())) {
            return CreditDeductionResult.skipped(aiUsageLogId, usageLog.getUserId(), 0, balanceBefore, "NOT_SUCCESS");
        }

        if (creditMapper.existsTransactionByAiUsageLogIdAndType(aiUsageLogId, TRANSACTION_TYPE_AI_USAGE)) {
            return CreditDeductionResult.skipped(
                    aiUsageLogId,
                    usageLog.getUserId(),
                    requestedCreditUsed == null ? usageLog.creditUsedValue() : requestedCreditUsed,
                    balanceBefore,
                    "ALREADY_DEDUCTED");
        }

        int creditUsed = requestedCreditUsed == null ? usageLog.creditUsedValue() : requestedCreditUsed;
        if (creditUsed <= 0) {
            // 호출자가 0 크레딧 정책을 명시한 경우에도 집계 기준은 로그에 맞춰 둔다.
            if (requestedCreditUsed != null && requestedCreditUsed != usageLog.creditUsedValue()) {
                creditMapper.updateAiUsageCreditUsed(aiUsageLogId, requestedCreditUsed);
            }
            return CreditDeductionResult.skipped(aiUsageLogId, usageLog.getUserId(), creditUsed, balanceBefore, "NO_CREDIT_USED");
        }

        if (requestedCreditUsed != null && requestedCreditUsed != usageLog.creditUsedValue()) {
            creditMapper.updateAiUsageCreditUsed(aiUsageLogId, requestedCreditUsed);
        }

        int updated = creditMapper.deductUserCreditIfEnough(usageLog.getUserId(), creditUsed);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CREDIT, "크레딧이 부족합니다.");
        }

        int balanceAfter = requireCurrentCredit(usageLog.getUserId());
        creditMapper.insertCreditTransaction(CreditTransaction.builder()
                .userId(usageLog.getUserId())
                .aiUsageLogId(aiUsageLogId)
                .type(TRANSACTION_TYPE_AI_USAGE)
                .amount(-creditUsed)
                .balanceAfter(balanceAfter)
                .featureType(usageLog.getFeatureType())
                .reason("AI 사용량 기반 크레딧 차감")
                .build());

        return CreditDeductionResult.deducted(aiUsageLogId, usageLog.getUserId(), creditUsed, balanceAfter);
    }

    @Override
    @Transactional
    public int grantCredit(Long userId, int amount, String type, String featureType, String reason) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "적립 크레딧은 1 이상이어야 합니다.");
        }
        int updated = creditMapper.addUserCredit(userId, amount);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없거나 크레딧 최대치를 초과했습니다.");
        }
        int balanceAfter = requireCurrentCredit(userId);
        creditMapper.insertCreditTransaction(CreditTransaction.builder()
                .userId(userId)
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .featureType(featureType)
                .reason(reason)
                .build());
        return balanceAfter;
    }

    private int requireCurrentCredit(Long userId) {
        Integer credit = creditMapper.findUserCredit(userId);
        if (credit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return credit;
    }
}
