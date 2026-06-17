package com.careertuner.billing.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.billing.domain.BenefitTransaction;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.domain.UserBenefitBalance;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.dto.AiFeatureBenefitPolicyResponse;
import com.careertuner.billing.dto.BenefitConsumeResult;
import com.careertuner.billing.dto.BenefitTransactionResponse;
import com.careertuner.billing.dto.MyBenefitsResponse;
import com.careertuner.billing.dto.SubscriptionBenefitPolicyResponse;
import com.careertuner.billing.dto.SubscriptionPlanResponse;
import com.careertuner.billing.dto.UserBenefitBalanceResponse;
import com.careertuner.billing.mapper.BillingMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService, AiBenefitUsageService {

    private static final String DEFAULT_PLAN = "FREE";
    private static final String TRANSACTION_GRANT = "GRANT";
    private static final String TRANSACTION_CONSUME = "CONSUME";
    private static final String REF_BENEFIT_BALANCE = "BENEFIT_BALANCE";

    private final BillingMapper billingMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> listPlans() {
        List<SubscriptionPlan> plans = billingMapper.findActivePlans();
        Map<String, List<SubscriptionBenefitPolicyResponse>> benefitsByPlan = billingMapper.findActiveBenefitPolicies()
                .stream()
                .map(SubscriptionBenefitPolicyResponse::from)
                .collect(Collectors.groupingBy(SubscriptionBenefitPolicyResponse::planCode));

        return plans.stream()
                .map(plan -> SubscriptionPlanResponse.of(
                        plan,
                        benefitsByPlan.getOrDefault(plan.getCode(), List.of()).stream()
                                .sorted(Comparator.comparingInt(SubscriptionBenefitPolicyResponse::sortOrder))
                                .toList()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiFeatureBenefitPolicyResponse> listFeatureBenefitPolicies() {
        return billingMapper.findActiveFeatureBenefitPolicies().stream()
                .map(AiFeatureBenefitPolicyResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public MyBenefitsResponse myBenefits(Long userId) {
        BenefitPeriod period = currentBenefitPeriod(userId);
        ensureBalances(userId, period);
        Map<String, String> benefitNames = benefitNames(period.planCode());
        List<UserBenefitBalanceResponse> balances = billingMapper
                .findBenefitBalances(userId, period.start(), period.end())
                .stream()
                .map(balance -> UserBenefitBalanceResponse.of(
                        balance,
                        benefitNames.getOrDefault(balance.getBenefitCode(), balance.getBenefitCode())))
                .toList();
        return new MyBenefitsResponse(period.planCode(), period.start(), period.end(), balances);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BenefitTransactionResponse> myBenefitTransactions(Long userId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return billingMapper.findBenefitTransactionsByUser(userId, normalizedLimit).stream()
                .map(BenefitTransactionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public MyBenefitsResponse activateSubscriptionAfterPayment(Long userId, String planCode) {
        String normalizedPlanCode = normalizePlanCode(planCode);
        if (DEFAULT_PLAN.equals(normalizedPlanCode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Free plan does not require payment.");
        }
        SubscriptionPlan plan = billingMapper.findActivePlanByCode(normalizedPlanCode);
        if (plan == null || plan.getMonthlyPrice() <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Purchasable subscription plan was not found.");
        }

        LocalDateTime now = LocalDateTime.now();
        billingMapper.deactivateActiveSubscriptions(userId, now);

        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setPlanCode(normalizedPlanCode);
        subscription.setStatus("ACTIVE");
        subscription.setStartedAt(now);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusMonths(1));
        billingMapper.insertUserSubscription(subscription);

        int updated = billingMapper.updateUserPlan(userId, normalizedPlanCode);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Subscription user was not found.");
        }

        BenefitPeriod period = new BenefitPeriod(
                normalizedPlanCode,
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd());
        ensureBalances(userId, period);
        return myBenefits(userId);
    }

    @Override
    @Transactional
    public BenefitConsumeResult consumeByFeature(Long userId,
                                                 String featureType,
                                                 String refType,
                                                 Long refId,
                                                 Long aiUsageLogId,
                                                 String reason) {
        if (isBlank(featureType) || isBlank(refType) || refId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용권 차감 기준이 올바르지 않습니다.");
        }
        var featurePolicy = billingMapper.findActiveFeatureBenefitPolicy(featureType);
        if (featurePolicy == null || !featurePolicy.isIncludedInTicket()) {
            return BenefitConsumeResult.skipped(null, 0, "NO_BENEFIT_POLICY");
        }

        BenefitPeriod period = currentBenefitPeriod(userId);
        ensureBalances(userId, period);

        String benefitCode = featurePolicy.getBenefitCode();
        if (billingMapper.existsConsumeTransaction(benefitCode, refType, refId)) {
            UserBenefitBalance current = billingMapper.findBenefitBalance(userId, benefitCode, period.start());
            int remaining = current == null ? 0 : current.getRemainingQuantity();
            return BenefitConsumeResult.skipped(benefitCode, remaining, "ALREADY_CONSUMED");
        }

        UserBenefitBalance balance = billingMapper.findBenefitBalance(userId, benefitCode, period.start());
        if (balance == null || balance.getRemainingQuantity() <= 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CREDIT, "사용 가능한 사용권이 부족합니다.");
        }

        int updated = billingMapper.consumeBenefitIfEnough(balance.getId());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CREDIT, "사용 가능한 사용권이 부족합니다.");
        }

        UserBenefitBalance latest = billingMapper.findBenefitBalance(userId, benefitCode, period.start());
        billingMapper.insertBenefitTransaction(BenefitTransaction.builder()
                .userId(userId)
                .benefitCode(benefitCode)
                .transactionType(TRANSACTION_CONSUME)
                .amount(-1)
                .balanceAfter(latest.getRemainingQuantity())
                .refType(refType)
                .refId(refId)
                .aiUsageLogId(aiUsageLogId)
                .reason(reason == null || reason.isBlank() ? "AI 기능 사용권 차감" : reason)
                .build());
        return BenefitConsumeResult.consumed(benefitCode, latest.getRemainingQuantity());
    }

    private void ensureBalances(Long userId, BenefitPeriod period) {
        List<SubscriptionBenefitPolicy> policies = billingMapper.findActiveBenefitPoliciesByPlan(period.planCode());
        if (policies.isEmpty() && !DEFAULT_PLAN.equals(period.planCode())) {
            policies = billingMapper.findActiveBenefitPoliciesByPlan(DEFAULT_PLAN);
        }

        for (SubscriptionBenefitPolicy policy : policies) {
            if (billingMapper.findBenefitBalance(userId, policy.getBenefitCode(), period.start()) != null) {
                continue;
            }
            UserBenefitBalance balance = new UserBenefitBalance();
            balance.setUserId(userId);
            balance.setBenefitCode(policy.getBenefitCode());
            balance.setPeriodStart(period.start());
            balance.setPeriodEnd(period.end());
            balance.setGrantedQuantity(policy.getQuantity());
            balance.setUsedQuantity(0);
            balance.setRemainingQuantity(policy.getQuantity());
            balance.setSourcePlanCode(policy.getPlanCode());
            try {
                billingMapper.insertBenefitBalance(balance);
                billingMapper.insertBenefitTransaction(BenefitTransaction.builder()
                        .userId(userId)
                        .benefitCode(policy.getBenefitCode())
                        .transactionType(TRANSACTION_GRANT)
                        .amount(policy.getQuantity())
                        .balanceAfter(policy.getQuantity())
                        .refType(REF_BENEFIT_BALANCE)
                        .refId(balance.getId())
                        .reason("구독 플랜 월 사용권 지급")
                        .build());
            } catch (DuplicateKeyException ignored) {
                // Another request initialized the same period first.
            }
        }
    }

    private BenefitPeriod currentBenefitPeriod(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        UserSubscription subscription = billingMapper.findActiveSubscription(userId, now);
        if (subscription != null) {
            return new BenefitPeriod(
                    normalizePlanCode(subscription.getPlanCode()),
                    subscription.getCurrentPeriodStart(),
                    subscription.getCurrentPeriodEnd());
        }

        LocalDate firstDay = now.toLocalDate().withDayOfMonth(1);
        return new BenefitPeriod(
                currentPlanCode(userId),
                LocalDateTime.of(firstDay, LocalTime.MIN),
                LocalDateTime.of(firstDay.plusMonths(1), LocalTime.MIN));
    }

    private String currentPlanCode(Long userId) {
        String planCode = billingMapper.findUserPlanCode(userId);
        return normalizePlanCode(planCode);
    }

    private String normalizePlanCode(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return DEFAULT_PLAN;
        }
        return planCode.trim().toUpperCase();
    }

    private Map<String, String> benefitNames(String planCode) {
        return billingMapper.findActiveBenefitPoliciesByPlan(planCode).stream()
                .collect(Collectors.toMap(
                        SubscriptionBenefitPolicy::getBenefitCode,
                        SubscriptionBenefitPolicy::getBenefitName,
                        (left, right) -> left));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record BenefitPeriod(String planCode, LocalDateTime start, LocalDateTime end) {
    }
}
