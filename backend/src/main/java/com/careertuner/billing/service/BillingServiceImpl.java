package com.careertuner.billing.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.billing.domain.BenefitTransaction;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.domain.UserBenefitBalance;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.dto.AdminPaymentRow;
import com.careertuner.billing.dto.AdminPaymentSummary;
import com.careertuner.billing.dto.AiFeatureBenefitPolicyResponse;
import com.careertuner.billing.dto.BenefitConsumeResult;
import com.careertuner.billing.dto.BenefitTransactionResponse;
import com.careertuner.billing.dto.MyBillingResponse;
import com.careertuner.billing.dto.MyBenefitsResponse;
import com.careertuner.billing.dto.SubscriptionBenefitPolicyResponse;
import com.careertuner.billing.dto.SubscriptionPlanResponse;
import com.careertuner.billing.dto.UserBenefitBalanceResponse;
import com.careertuner.billing.dto.UsageRow;
import com.careertuner.billing.mapper.BillingMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.domain.CreditProduct;
import com.careertuner.credit.domain.CreditTransaction;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.payment.domain.Payment;

import lombok.RequiredArgsConstructor;

/**
 * 결제/구독/크레딧 도메인 서비스.
 * 개발 단계에서는 외부 PG(토스 등) 연동 없이 구매를 즉시 PAID 로 기록한다.
 * 실제 PG 연동 시 subscribe/purchaseCredits 의 결제 승인 단계만 교체하면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingServiceImpl implements BillingService, AiBenefitUsageService {

    private static final String DEV_PROVIDER = "DEV";
    private static final String DEFAULT_PLAN = "FREE";
    private static final String TRANSACTION_GRANT = "GRANT";
    private static final String TRANSACTION_CONSUME = "CONSUME";
    private static final String REF_BENEFIT_BALANCE = "BENEFIT_BALANCE";
    private static final String SOURCE_TYPE_PLAN = "PLAN";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BillingMapper billingMapper;
    private final BillingPolicyService billingPolicyService;
    private final NotificationService notificationService;

    @Override
    public List<SubscriptionPlan> getPlans() {
        return billingPolicyService.activePlans();
    }

    @Override
    public List<SubscriptionPlanResponse> listPlans() {
        Map<String, List<SubscriptionBenefitPolicyResponse>> benefitsByPlan = billingPolicyService.activeBenefitPolicies()
                .stream()
                .map(SubscriptionBenefitPolicyResponse::from)
                .collect(Collectors.groupingBy(SubscriptionBenefitPolicyResponse::planCode));

        return billingPolicyService.activePlans().stream()
                .map(plan -> SubscriptionPlanResponse.of(
                        plan,
                        benefitsByPlan.getOrDefault(plan.getCode(), List.of()).stream()
                                .sorted(Comparator.comparingInt(SubscriptionBenefitPolicyResponse::sortOrder))
                                .toList()))
                .toList();
    }

    @Override
    public List<AiFeatureBenefitPolicyResponse> listFeatureBenefitPolicies() {
        return billingPolicyService.activeFeatureBenefitPolicies().stream()
                .map(AiFeatureBenefitPolicyResponse::from)
                .toList();
    }

    @Override
    public List<CreditProduct> getCreditProducts() {
        return billingPolicyService.enabledCreditProducts();
    }

    @Override
    public MyBillingResponse getMyBilling(Long userId) {
        UserSubscription sub = billingMapper.findActiveSubscription(userId, LocalDateTime.now());
        int balance = balanceOf(userId);
        if (sub == null) {
            SubscriptionPlan free = billingMapper.findPlanByCode("FREE");
            return new MyBillingResponse("FREE", free != null ? free.getName() : "무료", "NONE", null, balance);
        }
        SubscriptionPlan plan = billingMapper.findPlanByCode(sub.getPlanCode());
        return new MyBillingResponse(
                sub.getPlanCode(),
                plan != null ? plan.getName() : sub.getPlanCode(),
                sub.getStatus(),
                sub.getCurrentPeriodEnd(),
                balance);
    }

    @Override
    public List<Payment> getMyPayments(Long userId) {
        return billingMapper.findPaymentsByUserId(userId);
    }

    @Override
    public List<UsageRow> getMonthlyUsage(Long userId) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return billingMapper.monthlyUsage(userId, monthStart);
    }

    @Override
    public List<CreditTransaction> getMyCreditTransactions(Long userId) {
        return billingMapper.findCreditTransactionsByUserId(userId);
    }

    @Override
    @Transactional
    public MyBenefitsResponse myBenefits(Long userId) {
        BenefitPeriod period = currentBenefitPeriod(userId);
        ensureBalances(userId, period);
        Map<String, String> benefitNames = benefitNames(period);
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
    public List<BenefitTransactionResponse> myBenefitTransactions(Long userId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return billingMapper.findBenefitTransactionsByUser(userId, normalizedLimit).stream()
                .map(BenefitTransactionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public MyBillingResponse subscribe(Long userId, String planCode, String cycle) {
        String code = planCode == null ? "" : planCode.trim().toUpperCase();
        // FREE 선택은 구독 해지로 간주한다.
        if (DEFAULT_PLAN.equals(code)) {
            return cancelSubscription(userId);
        }
        SubscriptionPlan plan = billingPolicyService.activePlanByCode(code);
        if (plan == null || !plan.isActive()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "요금제를 찾을 수 없습니다.");
        }
        boolean yearly = cycle != null && cycle.trim().equalsIgnoreCase("YEARLY");
        int amount = yearly
                ? (plan.getYearlyPrice() != null ? plan.getYearlyPrice() : 0)
                : (plan.getMonthlyPrice() != null ? plan.getMonthlyPrice() : 0);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = yearly ? now.plusYears(1) : now.plusMonths(1);
        String policySnapshotJson = billingPolicyService.subscriptionSnapshotJson(code);

        Payment payment = recordPayment(userId, code, amount, code, null, policySnapshotJson);
        activateSubscription(userId, payment.getId(), code, now, end, policySnapshotJson);

        notify(userId, "PAYMENT_COMPLETE", "구독이 시작되었습니다",
                "%s 플랜(%s) 구독이 활성화되었습니다.".formatted(plan.getName(), yearly ? "연간" : "월간"));
        return getMyBilling(userId);
    }

    @Override
    @Transactional
    public MyBenefitsResponse activateSubscriptionAfterPayment(Long userId, Long paymentId, String planCode,
                                                               String policySnapshotJson) {
        String code = normalizePlanCode(planCode);
        if (DEFAULT_PLAN.equals(code)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "무료 플랜은 결제가 필요하지 않습니다.");
        }
        SubscriptionPlan plan = billingPolicyService.activePlanByCode(code);
        if (isBlank(policySnapshotJson) && (plan == null || plan.getMonthlyPrice() == null || plan.getMonthlyPrice() <= 0)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "구매 가능한 구독 플랜을 찾을 수 없습니다.");
        }
        String snapshot = isBlank(policySnapshotJson)
                ? billingPolicyService.baseSubscriptionSnapshotJson(code)
                : policySnapshotJson;

        LocalDateTime now = LocalDateTime.now();
        activateSubscription(userId, paymentId, code, now, now.plusMonths(1), snapshot);
        return myBenefits(userId);
    }

    @Override
    @Transactional
    public int grantCreditsAfterPayment(Long userId, String productCode, int creditAmount) {
        if (creditAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지급 크레딧 수량이 올바르지 않습니다.");
        }
        int updated = billingMapper.increaseUserCredit(userId, creditAmount);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "크레딧을 지급할 사용자를 찾을 수 없습니다.");
        }
        int balanceAfter = balanceOf(userId);
        billingMapper.insertCreditTransaction(CreditTransaction.builder()
                .userId(userId)
                .type("CHARGE")
                .amount(creditAmount)
                .balanceAfter(balanceAfter)
                .reason((isBlank(productCode) ? "크레딧" : productCode) + " 결제 충전")
                .build());
        return balanceAfter;
    }

    @Override
    @Transactional
    public MyBillingResponse purchaseCredits(Long userId, String productCode) {
        String code = productCode == null ? "" : productCode.trim().toUpperCase();
        CreditProduct product = billingPolicyService.enabledCreditProductByCode(code);
        if (product == null || !product.isEnabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "충전 상품을 찾을 수 없습니다.");
        }
        grantCreditsAfterPayment(userId, product.getName(), product.getCreditAmount());

        recordPayment(userId, code, product.getPrice(), null, product.getCreditAmount(),
                billingPolicyService.creditProductSnapshotJson(product));

        notify(userId, "CREDIT_RECHARGED", "크레딧이 충전되었습니다",
                "크레딧 %d개가 충전되어 현재 %d개 보유 중입니다.".formatted(product.getCreditAmount(), balanceOf(userId)));
        return getMyBilling(userId);
    }

    @Override
    @Transactional
    public MyBillingResponse cancelSubscription(Long userId) {
        UserSubscription subscription = billingMapper.findActiveSubscription(userId, LocalDateTime.now());
        int updated = billingMapper.cancelActiveSubscription(userId);
        if (updated > 0 && subscription != null) {
            String endDate = subscription.getCurrentPeriodEnd() == null
                    ? "현재 결제 기간 종료일"
                    : subscription.getCurrentPeriodEnd().format(DATE_FORMATTER);
            notify(userId, "SUBSCRIPTION_CANCELED", "구독 해지가 예약되었습니다",
                    "%s 플랜은 %s까지 이용할 수 있습니다. 이후 자동 갱신되지 않습니다."
                            .formatted(subscription.getPlanCode(), endDate));
        }
        return getMyBilling(userId);
    }

    @Override
    public List<AdminPaymentRow> getAllPayments(String status) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return billingMapper.findAllPayments(normalized);
    }

    @Override
    public AdminPaymentSummary getPaymentSummary() {
        return billingMapper.paymentSummary();
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
        var featurePolicy = billingPolicyService.activeFeatureBenefitPolicy(userId, featureType);
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
                .reason(isBlank(reason) ? "AI 기능 사용권 차감" : reason)
                .build());
        return BenefitConsumeResult.consumed(benefitCode, latest.getRemainingQuantity());
    }

    // ───── 내부 ─────

    private void activateSubscription(Long userId, Long paymentId, String planCode, LocalDateTime start, LocalDateTime end,
                                      String policySnapshotJson) {
        billingMapper.deactivateActiveSubscriptions(userId, start);
        billingMapper.insertSubscription(UserSubscription.builder()
                .paymentId(paymentId)
                .userId(userId)
                .planCode(planCode)
                .status("ACTIVE")
                .startedAt(start)
                .currentPeriodStart(start)
                .currentPeriodEnd(end)
                .policySnapshotJson(policySnapshotJson)
                .build());

        int updated = billingMapper.updateUserPlan(userId, planCode);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "구독 사용자를 찾을 수 없습니다.");
        }

        ensureBalances(userId, new BenefitPeriod(planCode, start, end, policySnapshotJson, true));
    }

    private void ensureBalances(Long userId, BenefitPeriod period) {
        List<SubscriptionBenefitPolicy> policies = benefitPoliciesFor(period);
        if (policies.isEmpty() && !DEFAULT_PLAN.equals(period.planCode())) {
            policies = period.subscriptionPeriod()
                    ? billingPolicyService.activeBenefitPoliciesForSubscriptionPeriod(DEFAULT_PLAN, period.policySnapshotJson())
                    : billingPolicyService.activeBenefitPoliciesByPlan(DEFAULT_PLAN, null);
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
            balance.setSourceType(SOURCE_TYPE_PLAN);
            balance.setSourceCode(policy.getPlanCode());
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
                // Another request initialized the same benefit period first.
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
                    subscription.getCurrentPeriodEnd(),
                    subscription.getPolicySnapshotJson(),
                    true);
        }

        LocalDate firstDay = now.toLocalDate().withDayOfMonth(1);
        return new BenefitPeriod(
                currentPlanCode(userId),
                LocalDateTime.of(firstDay, LocalTime.MIN),
                LocalDateTime.of(firstDay.plusMonths(1), LocalTime.MIN),
                null,
                false);
    }

    private String currentPlanCode(Long userId) {
        return normalizePlanCode(billingMapper.findUserPlanCode(userId));
    }

    private String normalizePlanCode(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return DEFAULT_PLAN;
        }
        return planCode.trim().toUpperCase();
    }

    private Map<String, String> benefitNames(BenefitPeriod period) {
        return benefitPoliciesFor(period).stream()
                .collect(Collectors.toMap(
                        SubscriptionBenefitPolicy::getBenefitCode,
                        SubscriptionBenefitPolicy::getBenefitName,
                        (left, right) -> left));
    }

    private int balanceOf(Long userId) {
        Integer balance = billingMapper.findUserCredit(userId);
        return balance != null ? balance : 0;
    }

    private Payment recordPayment(Long userId, String productCode, int amount, String plan, Integer creditAmount,
                                  String policySnapshotJson) {
        LocalDateTime now = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setProvider(DEV_PROVIDER);
        payment.setProductType(plan == null ? "CREDIT" : "SUBSCRIPTION");
        payment.setProductCode(productCode);
        payment.setOrderId("DEV-" + UUID.randomUUID());
        payment.setPaymentKey("DEVKEY-" + UUID.randomUUID());
        payment.setAmount(amount);
        payment.setPlan(plan);
        payment.setCreditAmount(creditAmount);
        payment.setPolicySnapshotJson(policySnapshotJson);
        payment.setStatus("PAID");
        payment.setPaidAt(now);
        billingMapper.insertPayment(payment);
        return payment;
    }

    private void notify(Long userId, String type, String title, String message) {
        notificationService.notify(Notification.builder()
                .userId(userId)
                .type(type)
                .targetType("BILLING")
                .title(title)
                .message(message)
                .link("/billing?tab=history")
                .build());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<SubscriptionBenefitPolicy> benefitPoliciesFor(BenefitPeriod period) {
        if (period.subscriptionPeriod()) {
            return billingPolicyService.activeBenefitPoliciesForSubscriptionPeriod(
                    period.planCode(), period.policySnapshotJson());
        }
        return billingPolicyService.activeBenefitPoliciesByPlan(period.planCode(), period.policySnapshotJson());
    }

    private record BenefitPeriod(String planCode,
                                 LocalDateTime start,
                                 LocalDateTime end,
                                 String policySnapshotJson,
                                 boolean subscriptionPeriod) {
    }
}
