package com.careertuner.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.payment.domain.Payment;

@Mapper
public interface PaymentMapper {

    void insertPayment(Payment payment);

    Payment findByOrderId(@Param("orderId") String orderId);

    boolean existsByPaymentKey(@Param("paymentKey") String paymentKey);

    int markPaidIfReady(@Param("orderId") String orderId,
                        @Param("paymentKey") String paymentKey);

    int markCanceledIfReady(@Param("orderId") String orderId);

    Integer findUserCredit(@Param("userId") Long userId);
}
