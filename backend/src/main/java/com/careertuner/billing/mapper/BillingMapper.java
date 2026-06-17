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

@Mapper
public interface BillingMapper {

    List<SubscriptionPlan> findActivePlans();

    SubscriptionPlan findActivePlanByCode(@Param("planCode") String planCode);

    List<SubscriptionBenefitPolicy> findActiveBenefitPolicies();

    List<SubscriptionBenefitPolicy> findActiveBenefitPoliciesByPlan(@Param("planCode") String planCode);

    SubscriptionBenefitPolicy findActiveBenefitPolicy(@Param("planCode") String planCode,
                                                      @Param("benefitCode") String benefitCode);

    List<AiFeatureBenefitPolicy> findActiveFeatureBenefitPolicies();

    AiFeatureBenefitPolicy findActiveFeatureBenefitPolicy(@Param("featureType") String featureType);

    UserSubscription findActiveSubscription(@Param("userId") Long userId,
                                            @Param("now") LocalDateTime now);

    int deactivateActiveSubscriptions(@Param("userId") Long userId,
                                      @Param("now") LocalDateTime now);

    void insertUserSubscription(UserSubscription subscription);

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

    int refundBenefit(@Param("balanceId") Long balanceId);

    boolean existsConsumeTransaction(@Param("benefitCode") String benefitCode,
                                     @Param("refType") String refType,
                                     @Param("refId") Long refId);

    void insertBenefitTransaction(BenefitTransaction transaction);

    List<BenefitTransaction> findBenefitTransactionsByUser(@Param("userId") Long userId,
                                                           @Param("limit") int limit);
}
