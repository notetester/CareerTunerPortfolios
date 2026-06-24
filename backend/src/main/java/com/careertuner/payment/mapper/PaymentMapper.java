package com.careertuner.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.payment.domain.Payment;

@Mapper
public interface PaymentMapper {

    /** 결제창 호출 전에 서버가 확정한 결제 대기 건을 저장한다. */
    void insertPayment(Payment payment);

    /** Toss 승인 요청 검증을 위해 주문번호로 결제 건을 조회한다. */
    Payment findByOrderId(@Param("orderId") String orderId);

    /** 같은 paymentKey가 이미 다른 결제 건에 저장되어 있는지 확인한다. */
    boolean existsByPaymentKey(@Param("paymentKey") String paymentKey);

    /** READY 상태 결제 건만 PAID로 전환해 중복 충전을 방지한다. */
    int markPaidIfReady(@Param("orderId") String orderId,
                        @Param("paymentKey") String paymentKey);

    /** 충전 후 사용자 크레딧 잔액을 조회한다. */
    Integer findUserCredit(@Param("userId") Long userId);
}
