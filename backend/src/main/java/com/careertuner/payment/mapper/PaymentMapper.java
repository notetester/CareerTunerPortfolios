package com.careertuner.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.payment.domain.Payment;

@Mapper
public interface PaymentMapper {

    void insertPayment(Payment payment);

    /** confirm/cancel 경쟁을 직렬화해 외부 승인과 지급이 같은 주문에서 중복 실행되지 않게 한다. */
    Payment findByOrderId(@Param("orderId") String orderId);

    boolean existsByPaymentKey(@Param("paymentKey") String paymentKey);

    int markPaidIfReady(@Param("orderId") String orderId,
                        @Param("paymentKey") String paymentKey);

    int markCanceledIfReady(@Param("orderId") String orderId);

    Integer findUserCredit(@Param("userId") Long userId);
}
