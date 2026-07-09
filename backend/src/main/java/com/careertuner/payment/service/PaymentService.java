package com.careertuner.payment.service;

import com.careertuner.payment.dto.TossPaymentCancelResponse;
import com.careertuner.payment.dto.TossPaymentConfirmRequest;
import com.careertuner.payment.dto.TossPaymentConfirmResponse;
import com.careertuner.payment.dto.TossPaymentReadyRequest;
import com.careertuner.payment.dto.TossPaymentReadyResponse;

public interface PaymentService {

    TossPaymentReadyResponse ready(Long userId, String email, TossPaymentReadyRequest request);

    TossPaymentConfirmResponse confirm(Long userId, TossPaymentConfirmRequest request);

    TossPaymentCancelResponse cancelReadyPayment(Long userId, String orderId);
}
