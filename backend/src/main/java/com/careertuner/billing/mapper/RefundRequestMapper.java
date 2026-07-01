package com.careertuner.billing.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.billing.domain.RefundRequest;
import com.careertuner.billing.dto.RefundRequestResponse;
import com.careertuner.payment.domain.Payment;

@Mapper
public interface RefundRequestMapper {
    Payment findOwnedPayment(@Param("paymentId") Long paymentId, @Param("userId") Long userId);

    boolean existsCreditUsageAfter(@Param("userId") Long userId, @Param("paidAt") LocalDateTime paidAt);

    boolean existsBenefitUsageAfter(@Param("userId") Long userId, @Param("paidAt") LocalDateTime paidAt);

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
