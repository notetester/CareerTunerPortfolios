package com.careertuner.billing.service;

import java.util.List;

import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.dto.AdminPaymentRow;
import com.careertuner.billing.dto.AdminPaymentSummary;
import com.careertuner.billing.dto.AiFeatureBenefitPolicyResponse;
import com.careertuner.billing.dto.BenefitTransactionResponse;
import com.careertuner.billing.dto.MyBillingResponse;
import com.careertuner.billing.dto.MyBenefitsResponse;
import com.careertuner.billing.dto.SubscriptionPlanResponse;
import com.careertuner.billing.dto.UsageRow;
import com.careertuner.credit.domain.CreditProduct;
import com.careertuner.credit.domain.CreditTransaction;
import com.careertuner.payment.domain.Payment;

public interface BillingService {

    List<SubscriptionPlan> getPlans();

    List<SubscriptionPlanResponse> listPlans();

    List<AiFeatureBenefitPolicyResponse> listFeatureBenefitPolicies();

    List<CreditProduct> getCreditProducts();

    MyBillingResponse getMyBilling(Long userId);

    List<Payment> getMyPayments(Long userId);

    List<UsageRow> getMonthlyUsage(Long userId);

    List<CreditTransaction> getMyCreditTransactions(Long userId);

    MyBenefitsResponse myBenefits(Long userId);

    List<BenefitTransactionResponse> myBenefitTransactions(Long userId, int limit);

    MyBillingResponse subscribe(Long userId, String planCode, String cycle);

    MyBenefitsResponse activateSubscriptionAfterPayment(Long userId, String planCode, String policySnapshotJson);

    int grantCreditsAfterPayment(Long userId, String productCode, int creditAmount);

    MyBillingResponse purchaseCredits(Long userId, String productCode);

    MyBillingResponse cancelSubscription(Long userId);

    // 관리자
    List<AdminPaymentRow> getAllPayments(String status);

    AdminPaymentSummary getPaymentSummary();
}
