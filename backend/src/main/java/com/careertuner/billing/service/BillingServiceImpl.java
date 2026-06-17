package com.careertuner.billing.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.billing.domain.CreditProduct;
import com.careertuner.billing.domain.CreditTransaction;
import com.careertuner.billing.domain.Payment;
import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.dto.AdminPaymentRow;
import com.careertuner.billing.dto.AdminPaymentSummary;
import com.careertuner.billing.dto.MyBillingResponse;
import com.careertuner.billing.dto.UsageRow;
import com.careertuner.billing.mapper.BillingMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;

/**
 * 결제/구독/크레딧 도메인 서비스.
 * 개발 단계에서는 외부 PG(토스 등) 연동 없이 구매를 즉시 PAID 로 기록한다.
 * 실제 PG 연동 시 subscribe/purchaseCredits 의 결제 승인 단계만 교체하면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingServiceImpl implements BillingService {

    private static final String DEV_PROVIDER = "DEV";

    private final BillingMapper billingMapper;
    private final NotificationMapper notificationMapper;

    @Override
    public List<SubscriptionPlan> getPlans() {
        return billingMapper.findActivePlans();
    }

    @Override
    public List<CreditProduct> getCreditProducts() {
        return billingMapper.findEnabledCreditProducts();
    }

    @Override
    public MyBillingResponse getMyBilling(Long userId) {
        UserSubscription sub = billingMapper.findActiveSubscription(userId);
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
    public MyBillingResponse subscribe(Long userId, String planCode, String cycle) {
        String code = planCode == null ? "" : planCode.trim().toUpperCase();
        // FREE 선택은 구독 해지로 간주한다.
        if ("FREE".equals(code)) {
            return cancelSubscription(userId);
        }
        SubscriptionPlan plan = billingMapper.findPlanByCode(code);
        if (plan == null || !plan.isActive()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "요금제를 찾을 수 없습니다.");
        }
        boolean yearly = cycle != null && cycle.trim().equalsIgnoreCase("YEARLY");
        int amount = yearly
                ? (plan.getYearlyPrice() != null ? plan.getYearlyPrice() : 0)
                : (plan.getMonthlyPrice() != null ? plan.getMonthlyPrice() : 0);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = yearly ? now.plusYears(1) : now.plusMonths(1);

        // 기존 활성 구독을 해지하고 새 구독을 활성화한다.
        billingMapper.cancelActiveSubscription(userId);
        billingMapper.insertSubscription(UserSubscription.builder()
                .userId(userId)
                .planCode(code)
                .status("ACTIVE")
                .startedAt(now)
                .currentPeriodStart(now)
                .currentPeriodEnd(end)
                .build());

        recordPayment(userId, code, amount, code, null);

        notify(userId, "PAYMENT_COMPLETE", "구독이 시작되었습니다",
                "%s 플랜(%s) 구독이 활성화되었습니다.".formatted(plan.getName(), yearly ? "연간" : "월간"));
        return getMyBilling(userId);
    }

    @Override
    @Transactional
    public MyBillingResponse purchaseCredits(Long userId, String productCode) {
        String code = productCode == null ? "" : productCode.trim().toUpperCase();
        CreditProduct product = billingMapper.findCreditProductByCode(code);
        if (product == null || !product.isEnabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "충전 상품을 찾을 수 없습니다.");
        }
        int credit = product.getCreditAmount() != null ? product.getCreditAmount() : 0;
        int newBalance = balanceOf(userId) + credit;

        billingMapper.insertCreditTransaction(CreditTransaction.builder()
                .userId(userId)
                .type("CHARGE")
                .amount(credit)
                .balanceAfter(newBalance)
                .reason(product.getName() + " 충전")
                .build());

        recordPayment(userId, code, product.getPrice() != null ? product.getPrice() : 0, null, credit);

        notify(userId, "CREDIT_RECHARGED", "크레딧이 충전되었습니다",
                "크레딧 %d개가 충전되어 현재 %d개 보유 중입니다.".formatted(credit, newBalance));
        return getMyBilling(userId);
    }

    @Override
    @Transactional
    public MyBillingResponse cancelSubscription(Long userId) {
        billingMapper.cancelActiveSubscription(userId);
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

    // ───── 내부 ─────

    private int balanceOf(Long userId) {
        Integer balance = billingMapper.latestCreditBalance(userId);
        return balance != null ? balance : 0;
    }

    private void recordPayment(Long userId, String productCode, int amount, String plan, Integer creditAmount) {
        LocalDateTime now = LocalDateTime.now();
        billingMapper.insertPayment(Payment.builder()
                .userId(userId)
                .provider(DEV_PROVIDER)
                .productCode(productCode)
                .orderId("DEV-" + UUID.randomUUID())
                .paymentKey("DEVKEY-" + UUID.randomUUID())
                .amount(amount)
                .plan(plan)
                .creditAmount(creditAmount)
                .status("PAID")
                .paidAt(now)
                .build());
    }

    private void notify(Long userId, String type, String title, String message) {
        notificationMapper.insert(Notification.builder()
                .userId(userId)
                .type(type)
                .targetType("BILLING")
                .title(title)
                .message(message)
                .link("/billing?tab=history")
                .build());
    }
}
