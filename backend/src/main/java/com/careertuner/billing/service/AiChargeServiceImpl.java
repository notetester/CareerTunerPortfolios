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
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChargeServiceImpl implements AiChargeService {

    private static final String REASON_ALREADY_CONSUMED = "ALREADY_CONSUMED";
    private static final String OVERAGE_CREDIT = "CREDIT";
    private static final String OVERAGE_FALLBACK_CREDIT = "FALLBACK_CREDIT";

    /** CREDIT_LOW 알림 임계값 — 차감으로 잔액이 이 값 미만으로 내려가는 순간에만 발행한다. */
    private static final int CREDIT_LOW_THRESHOLD = 10;

    private final BillingPolicyService billingPolicyService;
    private final RefundPolicyService refundPolicyService;
    private final AiBenefitUsageService benefitUsageService;
    private final CreditService creditService;
    private final CreditMapper creditMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public AiChargeResult charge(AiChargeCommand command) {
        validate(command);

        AiFeatureBenefitPolicy featurePolicy = billingPolicyService.activeFeatureBenefitPolicy(
                command.userId(), command.featureType());
        if (featurePolicy == null || !featurePolicy.isIncludedInTicket()) {
            return chargeCredit(command, creditCost(command, featurePolicy), "NO_TICKET_POLICY");
        }

        refundPolicyService.requireUsageAcknowledgement(
                command.userId(), RefundPolicyService.TRIGGER_BENEFIT_USE,
                command.policyAcknowledgementKey());
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
        refundPolicyService.requireUsageAcknowledgement(
                command.userId(), RefundPolicyService.TRIGGER_CREDIT_USE,
                command.policyAcknowledgementKey());
        if (command.aiUsageLogId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "크레딧 차감에는 AI 사용 로그가 필요합니다.");
        }

        CreditDeductionResult deducted = creditService.deductByAiUsageLog(command.aiUsageLogId(), creditCost);
        if (!deducted.deducted()) {
            return AiChargeResult.skipped(null, 0, deducted.balanceAfter(), deducted.reason());
        }
        notifyCreditLowIfCrossed(command.userId(), deducted);
        return AiChargeResult.credit(deducted.creditUsed(), deducted.balanceAfter());
    }

    /**
     * 차감으로 잔액이 임계값 미만으로 '내려가는 순간'(차감 전 >= 임계값, 후 < 임계값)에만
     * CREDIT_LOW 알림을 발행한다. 이미 낮은 잔액에서의 반복 차감엔 재발행하지 않는다.
     * 발행 실패가 차감 로직을 깨지 않도록 best-effort 처리한다.
     */
    private void notifyCreditLowIfCrossed(Long userId, CreditDeductionResult deducted) {
        int balanceAfter = deducted.balanceAfter();
        int balanceBefore = balanceAfter + deducted.creditUsed();
        if (balanceBefore < CREDIT_LOW_THRESHOLD || balanceAfter >= CREDIT_LOW_THRESHOLD) {
            return;
        }
        try {
            notificationService.notify(Notification.builder()
                    .userId(userId)
                    .type("CREDIT_LOW")
                    .targetType("BILLING")
                    .title("크레딧이 얼마 남지 않았습니다")
                    .message("남은 크레딧이 %d개입니다. 충전 후 AI 기능을 계속 이용할 수 있습니다.".formatted(balanceAfter))
                    .link("/billing")
                    .build());
        } catch (Exception e) {
            log.error("CREDIT_LOW 알림 발행 실패: userId={}", userId, e);
        }
    }

    private int creditCost(AiChargeCommand command, AiFeatureBenefitPolicy featurePolicy) {
        if (command.creditCost() != null) {
            return command.creditCost();
        }
        Integer usageCost = usageBasedCreditCost(command, featurePolicy);
        if (usageCost != null) {
            return usageCost;
        }
        return featurePolicy == null ? 0 : featurePolicy.getDefaultCreditCost();
    }

    private int creditCost(AiChargeCommand command,
                           AiFeatureBenefitPolicy featurePolicy,
                           SubscriptionBenefitPolicy benefitPolicy) {
        if (command.creditCost() != null) {
            return command.creditCost();
        }
        Integer usageCost = usageBasedCreditCost(command, featurePolicy);
        if (usageCost != null) {
            return usageCost;
        }
        if (benefitPolicy != null && benefitPolicy.getCreditCost() > 0) {
            return benefitPolicy.getCreditCost();
        }
        return featurePolicy == null ? 0 : featurePolicy.getDefaultCreditCost();
    }

    private Integer usageBasedCreditCost(AiChargeCommand command, AiFeatureBenefitPolicy policy) {
        if (policy == null || command.tokenUsage() == null || command.tokenUsage() <= 0
                || policy.getCreditUnitTokens() <= 0 || policy.getMaxCreditCost() <= 0) {
            return null;
        }
        int minimum = Math.max(0, policy.getMinCreditCost());
        int maximum = Math.max(minimum, policy.getMaxCreditCost());
        int calculated = (int) Math.ceil(command.tokenUsage() / (double) policy.getCreditUnitTokens());
        return Math.max(minimum, Math.min(maximum, calculated));
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
        if (command.tokenUsage() != null && command.tokenUsage() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "AI 토큰 사용량은 0 이상이어야 합니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
