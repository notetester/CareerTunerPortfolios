package com.careertuner.payment.service;

import com.careertuner.payment.dto.TossPaymentConfirmRequest;
import com.careertuner.payment.dto.TossPaymentConfirmResponse;
import com.careertuner.payment.dto.TossPaymentReadyRequest;
import com.careertuner.payment.dto.TossPaymentReadyResponse;

public interface PaymentService {

    /** 크레딧 상품 선택 이후 Toss 결제창 호출에 필요한 서버 확정 결제 건을 만든다. */
    TossPaymentReadyResponse ready(Long userId, String email, TossPaymentReadyRequest request);

    /** Toss 성공 리다이렉트 결과를 승인하고 성공한 결제만 사용자 크레딧으로 충전한다. */
    TossPaymentConfirmResponse confirm(Long userId, TossPaymentConfirmRequest request);
}
