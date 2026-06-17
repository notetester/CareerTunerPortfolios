package com.careertuner.billing.service;

import java.util.List;

import com.careertuner.billing.dto.AiFeatureBenefitPolicyResponse;
import com.careertuner.billing.dto.BenefitTransactionResponse;
import com.careertuner.billing.dto.MyBenefitsResponse;
import com.careertuner.billing.dto.SubscriptionPlanResponse;

public interface BillingService {

    List<SubscriptionPlanResponse> listPlans();

    List<AiFeatureBenefitPolicyResponse> listFeatureBenefitPolicies();

    MyBenefitsResponse myBenefits(Long userId);

    List<BenefitTransactionResponse> myBenefitTransactions(Long userId, int limit);

    MyBenefitsResponse activateSubscriptionAfterPayment(Long userId, String planCode);
}
