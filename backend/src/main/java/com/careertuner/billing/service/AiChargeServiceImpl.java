package com.careertuner.billing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.billing.domain.AiFeatureBenefitPolicy;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.dto.AiChargeCommand;
import com.careertuner.billing.dto.AiChargeResult;
import com.careertuner.billing.dto.BenefitConsumeResult;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.dto.CreditDeductionResult;
import com.careertuner.credit.mapper.CreditMapper;
import com.careertuner.credit.service.CreditService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiChargeServiceImpl implements AiChargeService {

    private static final String REASON_ALREADY_CONSUMED = "ALREADY_CONSUMED";
    private static final String OVERAGE_CREDIT = "CREDIT";
    private static final String OVERAGE_FALLBACK_CREDIT = "FALLBACK_CREDIT";

    private final BillingPolicyService billingPolicyService;
    private final AiBenefitUsageService benefitUsageService;
    private final CreditService creditService;
    private final CreditMapper creditMapper;

    @Override
    @Transactional
    public AiChargeResult charge(AiChargeCommand command) {
        validate(command);

        AiFeatureBenefitPolicy featurePolicy = billingPolicyService.activeFeatureBenefitPolicy(
                command.userId(), command.featureType());
        if (featurePolicy == null || !featurePolicy.isIncludedInTicket()) {
            return chargeCredit(command, creditCost(command, featurePolicy), "NO_TICKET_POLICY");
        }

        SubscriptionBenefitPolicy benefitPolicy = currentBenefitPolicy(command.userId(), featurePolicy.getBenefitCode());
        try {
            BenefitConsumeResult ticket = benefitUsageService.consumeByFeature(
                    command.userId(),
                    command.featureType(),
                    command.refType(),
                    command.refId(),
                    command.aiUsageLogId(),
                    command.reason());
            if (ticket.consumed()) {
                return AiChargeResult.ticket(ticket.benefitCode(), ticket.remainingQuantity());
            }
            if (REASON_ALREADY_CONSUMED.equals(ticket.reason())) {
                return AiChargeResult.skipped(
                        ticket.benefitCode(),
                        ticket.remainingQuantity(),
                        currentCredit(command),
                        "ALREADY_CHARGED");
            }
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.INSUFFICIENT_CREDIT) {
                throw ex;
            }
            if (!allowsCreditFallback(benefitPolicy)) {
                throw ex;
            }
        }

        if (!allowsCreditFallback(benefitPolicy)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CREDIT, "사용 가능한 사용권이 부족합니다.");
        }
        return chargeCredit(command, creditCost(command, featurePolicy, benefitPolicy), "TICKET_EMPTY");
    }

    private AiChargeResult chargeCredit(AiChargeCommand command, int creditCost, String skipReason) {
        if (creditCost <= 0) {
            return AiChargeResult.skipped(null, 0, currentCredit(command), skipReason);
        }
        if (command.aiUsageLogId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "크레딧 차감에는 AI 사용 로그가 필요합니다.");
        }

        CreditDeductionResult deducted = creditService.deductByAiUsageLog(command.aiUsageLogId(), creditCost);
        if (!deducted.deducted()) {
            return AiChargeResult.skipped(null, 0, deducted.balanceAfter(), deducted.reason());
        }
        return AiChargeResult.credit(deducted.creditUsed(), deducted.balanceAfter());
    }

    private int creditCost(AiChargeCommand command, AiFeatureBenefitPolicy featurePolicy) {
        if (command.creditCost() != null) {
            return command.creditCost();
        }
        return featurePolicy == null ? 0 : featurePolicy.getDefaultCreditCost();
    }

    private int creditCost(AiChargeCommand command,
                           AiFeatureBenefitPolicy featurePolicy,
                           SubscriptionBenefitPolicy benefitPolicy) {
        if (command.creditCost() != null) {
            return command.creditCost();
        }
        if (benefitPolicy != null && benefitPolicy.getCreditCost() > 0) {
            return benefitPolicy.getCreditCost();
        }
        return featurePolicy == null ? 0 : featurePolicy.getDefaultCreditCost();
    }

    private SubscriptionBenefitPolicy currentBenefitPolicy(Long userId, String benefitCode) {
        return billingPolicyService.activeBenefitPolicy(userId, benefitCode);
    }

    private boolean allowsCreditFallback(SubscriptionBenefitPolicy policy) {
        if (policy == null || policy.getOveragePolicy() == null) {
            return false;
        }
        String overage = policy.getOveragePolicy().trim().toUpperCase();
        return OVERAGE_CREDIT.equals(overage) || OVERAGE_FALLBACK_CREDIT.equals(overage);
    }

    private int currentCredit(AiChargeCommand command) {
        Integer credit = creditMapper.findUserCredit(command.userId());
        return credit == null ? 0 : credit;
    }

    private void validate(AiChargeCommand command) {
        if (command == null
                || command.userId() == null
                || isBlank(command.featureType())
                || isBlank(command.refType())
                || command.refId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "AI 차감 기준이 올바르지 않습니다.");
        }
        if (command.creditCost() != null && command.creditCost() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차감 크레딧은 0 이상이어야 합니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
