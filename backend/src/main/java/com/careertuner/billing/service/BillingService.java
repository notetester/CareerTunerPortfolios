package com.careertuner.billing.service;

import java.util.List;

import com.careertuner.billing.domain.CreditProduct;
import com.careertuner.billing.domain.CreditTransaction;
import com.careertuner.billing.domain.Payment;
import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.dto.AdminPaymentRow;
import com.careertuner.billing.dto.AdminPaymentSummary;
import com.careertuner.billing.dto.MyBillingResponse;
import com.careertuner.billing.dto.UsageRow;

public interface BillingService {

    List<SubscriptionPlan> getPlans();

    List<CreditProduct> getCreditProducts();

    MyBillingResponse getMyBilling(Long userId);

    List<Payment> getMyPayments(Long userId);

    List<UsageRow> getMonthlyUsage(Long userId);

    List<CreditTransaction> getMyCreditTransactions(Long userId);

    MyBillingResponse subscribe(Long userId, String planCode, String cycle);

    MyBillingResponse purchaseCredits(Long userId, String productCode);

    MyBillingResponse cancelSubscription(Long userId);

    // 관리자
    List<AdminPaymentRow> getAllPayments(String status);

    AdminPaymentSummary getPaymentSummary();
}
