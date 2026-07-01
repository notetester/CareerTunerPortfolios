package com.careertuner.billing.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.billing.domain.RefundRequest;
import com.careertuner.billing.domain.BenefitTransaction;
import com.careertuner.billing.domain.UserBenefitBalance;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.dto.RefundRequestResponse;
import com.careertuner.credit.domain.CreditTransaction;
import com.careertuner.payment.domain.Payment;

@Mapper
public interface RefundRequestMapper {
    Payment findOwnedPayment(@Param("paymentId") Long paymentId, @Param("userId") Long userId);

    boolean existsCreditUsageAfter(@Param("userId") Long userId, @Param("paidAt") LocalDateTime paidAt);

    boolean existsBenefitUsageAfter(@Param("userId") Long userId, @Param("paidAt") LocalDateTime paidAt);

    int deductUserCreditForRefund(@Param("userId") Long userId,
                                  @Param("creditAmount") int creditAmount);

    Integer findUserCredit(@Param("userId") Long userId);

    void insertCreditTransaction(CreditTransaction transaction);

    UserSubscription findSubscriptionForRefund(@Param("paymentId") Long paymentId,
                                               @Param("userId") Long userId,
                                               @Param("planCode") String planCode,
                                               @Param("paidAt") LocalDateTime paidAt);

    int attachPaymentToSubscription(@Param("subscriptionId") Long subscriptionId,
                                    @Param("paymentId") Long paymentId);

    List<UserBenefitBalance> findBenefitBalancesForRefund(@Param("userId") Long userId,
                                                          @Param("periodStart") LocalDateTime periodStart,
                                                          @Param("periodEnd") LocalDateTime periodEnd);

    int revokeBenefitBalanceIfUnused(@Param("balanceId") Long balanceId);

    void insertBenefitTransaction(BenefitTransaction transaction);

    int markSubscriptionRefunded(@Param("subscriptionId") Long subscriptionId);

    int resetUserPlanAfterSubscriptionRefund(@Param("userId") Long userId,
                                             @Param("refundedSubscriptionId") Long refundedSubscriptionId,
                                             @Param("now") LocalDateTime now);

    void insert(RefundRequest request);

    RefundRequestResponse findResponseById(@Param("id") Long id);

    List<RefundRequestResponse> findResponsesByUser(@Param("userId") Long userId);

    List<RefundRequestResponse> findAdminResponses(@Param("status") String status);

    int approve(@Param("id") Long id, @Param("adminId") Long adminId,
                @Param("reviewedReason") String reviewedReason);

    int reject(@Param("id") Long id, @Param("adminId") Long adminId,
               @Param("reviewedReason") String reviewedReason);

    int markPaymentRefunded(@Param("paymentId") Long paymentId);
}
