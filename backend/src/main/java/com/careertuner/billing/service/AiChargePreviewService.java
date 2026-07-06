package com.careertuner.billing.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.billing.domain.AiFeatureBenefitPolicy;
import com.careertuner.billing.domain.RefundPolicy;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.dto.AiChargePreviewRequest;
import com.careertuner.billing.dto.AiChargePreviewResponse;
import com.careertuner.billing.dto.MyBenefitsResponse;
import com.careertuner.billing.dto.UserBenefitBalanceResponse;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.mapper.CreditMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiChargePreviewService {

    private static final String CHARGE_TICKET = "TICKET";
    private static final String CHARGE_CREDIT = "CREDIT";
    private static final String CHARGE_FREE = "FREE";
    private static final String CHARGE_BLOCKED = "BLOCKED";
    private static final String OVERAGE_CREDIT = "CREDIT";
    private static final String OVERAGE_FALLBACK_CREDIT = "FALLBACK_CREDIT";

    private final BillingPolicyService billingPolicyService;
    private final BillingService billingService;
    private final CreditMapper creditMapper;
    private final RefundPolicyService refundPolicyService;

    @Transactional
    public AiChargePreviewResponse preview(Long userId, AiChargePreviewRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인 후 차감 정책을 확인할 수 있습니다.");
        }
        if (request == null || request.featureType() == null || request.featureType().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차감할 AI 기능 코드가 필요합니다.");
        }
        if (request.creditCost() != null && request.creditCost() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차감 크레딧은 0 이상이어야 합니다.");
        }
        String actionKey = normalizeActionKey(request.actionKey());
        String featureType = request.featureType().trim().toUpperCase(Locale.ROOT);
        RefundPolicy refundPolicy = refundPolicyService.currentPolicy();
        int currentCredit = currentCredit(userId);

        AiFeatureBenefitPolicy featurePolicy = billingPolicyService.activeFeatureBenefitPolicy(userId, featureType);
        CreditRange directRange = creditRange(request.creditCost(), featurePolicy, null);

        if (featurePolicy == null || !featurePolicy.isIncludedInTicket()) {
            return creditOrFree(featureType, null, directRange, currentCredit, actionKey, refundPolicy);
        }

        MyBenefitsResponse benefits = billingService.myBenefits(userId);
        UserBenefitBalanceResponse balance = benefits.benefits().stream()
                .filter(item -> featurePolicy.getBenefitCode().equals(item.benefitCode()))
                .findFirst()
                .orElse(null);
        int remainingTicket = balance == null ? 0 : balance.remainingQuantity();
        if (remainingTicket > 0) {
            return response(
                    featureType, CHARGE_TICKET, featurePolicy.getBenefitCode(), 1, CreditRange.free(),
                    remainingTicket, currentCredit, true,
                    RefundPolicyService.TRIGGER_BENEFIT_USE, actionKey, refundPolicy);
        }

        SubscriptionBenefitPolicy benefitPolicy = billingPolicyService.activeBenefitPolicy(
                userId, featurePolicy.getBenefitCode());
        if (!allowsCreditFallback(benefitPolicy)) {
            return response(
                    featureType, CHARGE_BLOCKED, featurePolicy.getBenefitCode(), 0, CreditRange.free(),
                    0, currentCredit, false,
                    RefundPolicyService.TRIGGER_BENEFIT_USE, actionKey, refundPolicy);
        }
        CreditRange fallbackRange = creditRange(request.creditCost(), featurePolicy, benefitPolicy);
        return creditOrFree(
                featureType, featurePolicy.getBenefitCode(), fallbackRange, currentCredit, actionKey, refundPolicy);
    }

    private AiChargePreviewResponse creditOrFree(
            String featureType,
            String benefitCode,
            CreditRange range,
            int currentCredit,
            String actionKey,
            RefundPolicy refundPolicy) {
        if (range.maximum() <= 0) {
            return response(
                    featureType, CHARGE_FREE, benefitCode, 0, CreditRange.free(), 0, currentCredit, true,
                    null, actionKey, refundPolicy);
        }
        return response(
                featureType, CHARGE_CREDIT, benefitCode, range.minimum(), range, 0, currentCredit,
                currentCredit >= range.maximum(),
                RefundPolicyService.TRIGGER_CREDIT_USE, actionKey, refundPolicy);
    }

    private AiChargePreviewResponse response(
            String featureType,
            String chargeType,
            String benefitCode,
            int chargeAmount,
            CreditRange range,
            int remainingTicket,
            int currentCredit,
            boolean sufficient,
            String triggerType,
            String actionKey,
            RefundPolicy refundPolicy) {
        return new AiChargePreviewResponse(
                featureType,
                chargeType,
                benefitCode,
                chargeAmount,
                range.minimum(),
                range.maximum(),
                range.unitTokens(),
                range.usageBased(),
                remainingTicket,
                currentCredit,
                sufficient,
                triggerType,
                actionKey,
                refundPolicy.getId(),
                refundPolicy.getVersion(),
                refundPolicy.getTitle(),
                refundPolicy.getSummary(),
                refundPolicy.getRulesJson());
    }

    private CreditRange creditRange(Integer requestedCost,
                                    AiFeatureBenefitPolicy featurePolicy,
                                    SubscriptionBenefitPolicy benefitPolicy) {
        if (requestedCost != null) {
            return CreditRange.fixed(requestedCost);
        }
        if (featurePolicy != null && featurePolicy.getMaxCreditCost() > 0) {
            int minimum = Math.max(0, featurePolicy.getMinCreditCost());
            int maximum = Math.max(minimum, featurePolicy.getMaxCreditCost());
            return new CreditRange(minimum, maximum, Math.max(0, featurePolicy.getCreditUnitTokens()));
        }
        if (benefitPolicy != null && benefitPolicy.getCreditCost() > 0) {
            return CreditRange.fixed(benefitPolicy.getCreditCost());
        }
        return CreditRange.fixed(featurePolicy == null ? 0 : featurePolicy.getDefaultCreditCost());
    }

    private int currentCredit(Long userId) {
        Integer credit = creditMapper.findUserCredit(userId);
        return credit == null ? 0 : credit;
    }

    private boolean allowsCreditFallback(SubscriptionBenefitPolicy policy) {
        if (policy == null || policy.getOveragePolicy() == null) {
            return false;
        }
        String overage = policy.getOveragePolicy().trim().toUpperCase(Locale.ROOT);
        return OVERAGE_CREDIT.equals(overage) || OVERAGE_FALLBACK_CREDIT.equals(overage);
    }

    private String normalizeActionKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isBlank() || key.length() > 120 || !key.matches("[A-Za-z0-9:_-]+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차감 정책 확인키가 올바르지 않습니다.");
        }
        return key;
    }

    private record CreditRange(int minimum, int maximum, int unitTokens) {
        static CreditRange fixed(int cost) {
            int normalized = Math.max(0, cost);
            return new CreditRange(normalized, normalized, 0);
        }

        static CreditRange free() {
            return fixed(0);
        }

        boolean usageBased() {
            return maximum > minimum && unitTokens > 0;
        }
    }
}
