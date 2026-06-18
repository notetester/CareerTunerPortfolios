package com.careertuner.billing.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.billing.domain.CreditProduct;
import com.careertuner.billing.domain.CreditTransaction;
import com.careertuner.billing.domain.Payment;
import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.dto.AdminPaymentRow;
import com.careertuner.billing.dto.AdminPaymentSummary;
import com.careertuner.billing.dto.UsageRow;

@Mapper
public interface BillingMapper {

    // 요금제 / 크레딧 상품
    List<SubscriptionPlan> findActivePlans();

    SubscriptionPlan findPlanByCode(@Param("code") String code);

    List<CreditProduct> findEnabledCreditProducts();

    CreditProduct findCreditProductByCode(@Param("code") String code);

    // 구독
    UserSubscription findActiveSubscription(@Param("userId") Long userId);

    void insertSubscription(UserSubscription subscription);

    int cancelActiveSubscription(@Param("userId") Long userId);

    // 결제
    List<Payment> findPaymentsByUserId(@Param("userId") Long userId);

    void insertPayment(Payment payment);

    // 크레딧 원장
    Integer latestCreditBalance(@Param("userId") Long userId);

    void insertCreditTransaction(CreditTransaction transaction);

    List<CreditTransaction> findCreditTransactionsByUserId(@Param("userId") Long userId);

    // 이번 달 AI 사용량(기능별 집계)
    List<UsageRow> monthlyUsage(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    // 관리자
    List<AdminPaymentRow> findAllPayments(@Param("status") String status);

    AdminPaymentSummary paymentSummary();
}
