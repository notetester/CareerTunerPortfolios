package com.careertuner.billing.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.billing.domain.AiFeatureBenefitPolicy;
import com.careertuner.billing.domain.BenefitTransaction;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.domain.UserBenefitBalance;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.dto.AdminPaymentRow;
import com.careertuner.billing.dto.AdminPaymentSummary;
import com.careertuner.billing.dto.UsageRow;
import com.careertuner.credit.domain.CreditProduct;
import com.careertuner.credit.domain.CreditTransaction;
import com.careertuner.payment.domain.Payment;

@Mapper
public interface BillingMapper {

    // 요금제 / 크레딧 상품
    List<SubscriptionPlan> findActivePlans();

    SubscriptionPlan findPlanByCode(@Param("code") String code);

    SubscriptionPlan findActivePlanByCode(@Param("planCode") String planCode);

    List<SubscriptionBenefitPolicy> findActiveBenefitPolicies();

    List<SubscriptionBenefitPolicy> findActiveBenefitPoliciesByPlan(@Param("planCode") String planCode);

    SubscriptionBenefitPolicy findActiveBenefitPolicy(@Param("planCode") String planCode,
                                                      @Param("benefitCode") String benefitCode);

    List<AiFeatureBenefitPolicy> findActiveFeatureBenefitPolicies();

    AiFeatureBenefitPolicy findActiveFeatureBenefitPolicy(@Param("featureType") String featureType);

    List<CreditProduct> findEnabledCreditProducts();

    CreditProduct findCreditProductByCode(@Param("code") String code);

    // 구독
    UserSubscription findActiveSubscription(@Param("userId") Long userId,
                                            @Param("now") LocalDateTime now);

    void insertSubscription(UserSubscription subscription);

    int cancelActiveSubscription(@Param("userId") Long userId);

    int deactivateActiveSubscriptions(@Param("userId") Long userId,
                                      @Param("now") LocalDateTime now);

    int updateUserPlan(@Param("userId") Long userId,
                       @Param("planCode") String planCode);

    String findUserPlanCode(@Param("userId") Long userId);

    List<UserBenefitBalance> findBenefitBalances(@Param("userId") Long userId,
                                                 @Param("periodStart") LocalDateTime periodStart,
                                                 @Param("periodEnd") LocalDateTime periodEnd);

    UserBenefitBalance findBenefitBalance(@Param("userId") Long userId,
                                          @Param("benefitCode") String benefitCode,
                                          @Param("periodStart") LocalDateTime periodStart);

    void insertBenefitBalance(UserBenefitBalance balance);

    int consumeBenefitIfEnough(@Param("balanceId") Long balanceId);

    boolean existsConsumeTransaction(@Param("benefitCode") String benefitCode,
                                     @Param("refType") String refType,
                                     @Param("refId") Long refId);

    void insertBenefitTransaction(BenefitTransaction transaction);

    List<BenefitTransaction> findBenefitTransactionsByUser(@Param("userId") Long userId,
                                                           @Param("limit") int limit);

    // 결제
    List<Payment> findPaymentsByUserId(@Param("userId") Long userId);

    void insertPayment(Payment payment);

    // 크레딧 원장
    Integer findUserCredit(@Param("userId") Long userId);

    int increaseUserCredit(@Param("userId") Long userId,
                           @Param("creditAmount") int creditAmount);

    void insertCreditTransaction(CreditTransaction transaction);

    List<CreditTransaction> findCreditTransactionsByUserId(@Param("userId") Long userId);

    // 이번 달 AI 사용량(기능별 집계)
    List<UsageRow> monthlyUsage(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    // 관리자
    List<AdminPaymentRow> findAllPayments(@Param("status") String status);

    AdminPaymentSummary paymentSummary();
}
