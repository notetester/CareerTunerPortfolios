package com.careertuner.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.domain.CreditProduct;
import com.careertuner.credit.mapper.CreditProductMapper;
import com.careertuner.payment.domain.Payment;
import com.careertuner.payment.dto.TossPaymentConfirmRequest;
import com.careertuner.payment.dto.TossPaymentConfirmResponse;
import com.careertuner.payment.dto.TossPaymentReadyRequest;
import com.careertuner.payment.dto.TossPaymentReadyResponse;
import com.careertuner.payment.mapper.PaymentMapper;
import com.careertuner.payment.service.TossPaymentClient.ConfirmedPayment;

class PaymentServiceImplTest {

    private final CreditProductMapper creditProductMapper = org.mockito.Mockito.mock(CreditProductMapper.class);
    private final PaymentMapper paymentMapper = org.mockito.Mockito.mock(PaymentMapper.class);
    private final TossPaymentClient tossPaymentClient = org.mockito.Mockito.mock(TossPaymentClient.class);
    private final TossPaymentProperties properties = tossProperties();
    private final PaymentServiceImpl service = new PaymentServiceImpl(
            creditProductMapper,
            paymentMapper,
            tossPaymentClient,
            properties);

    /** 결제 준비 단계에서 상품 스냅샷이 payment READY 건으로 저장되는지 검증한다. */
    @Test
    void readyCreatesPendingTossPaymentFromCreditProductSnapshot() {
        CreditProduct product = product("CREDIT_1000", "크레딧 1000개", 10000, 1000);
        when(creditProductMapper.findEnabledProductByCode("CREDIT_1000")).thenReturn(product);

        TossPaymentReadyResponse response = service.ready(
                1L,
                "user@careertuner.dev",
                new TossPaymentReadyRequest("CREDIT_1000"));

        assertThat(response.orderId()).startsWith("CT-");
        assertThat(response.orderName()).isEqualTo("크레딧 1000개");
        assertThat(response.amount()).isEqualTo(10000);
        assertThat(response.creditAmount()).isEqualTo(1000);
        assertThat(response.customerEmail()).isEqualTo("user@careertuner.dev");
        assertThat(response.successUrl()).isEqualTo("http://localhost:5173/billing/success");
        assertThat(response.failUrl()).isEqualTo("http://localhost:5173/billing/fail");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getProvider()).isEqualTo("TOSS");
        assertThat(saved.getProductCode()).isEqualTo("CREDIT_1000");
        assertThat(saved.getPaymentKey()).isNull();
        assertThat(saved.getAmount()).isEqualTo(10000);
        assertThat(saved.getPlan()).isNull();
        assertThat(saved.getCreditAmount()).isEqualTo(1000);
        assertThat(saved.getStatus()).isEqualTo("READY");
    }

    /** Toss 승인 성공 후 payment가 PAID 처리되고 사용자 크레딧이 증가하는지 검증한다. */
    @Test
    void confirmApprovesTossPaymentAndIncreasesUserCredit() {
        Payment payment = payment("order-1", 1L, 10000, 1000, "READY");
        when(paymentMapper.findByOrderId("order-1")).thenReturn(payment);
        when(paymentMapper.existsByPaymentKey("pay-key")).thenReturn(false);
        when(tossPaymentClient.confirm("pay-key", "order-1", 10000))
                .thenReturn(new ConfirmedPayment("pay-key", "order-1", 10000, "DONE"));
        when(paymentMapper.markPaidIfReady("order-1", "pay-key")).thenReturn(1);
        when(paymentMapper.increaseUserCredit(1L, 1000)).thenReturn(1);
        when(paymentMapper.findUserCredit(1L)).thenReturn(1500);

        TossPaymentConfirmResponse response = service.confirm(
                1L,
                new TossPaymentConfirmRequest("pay-key", "order-1", 10000));

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.balance()).isEqualTo(1500);
        verify(paymentMapper).markPaidIfReady("order-1", "pay-key");
        verify(paymentMapper).increaseUserCredit(1L, 1000);
    }

    /** 요청 금액이 DB 금액과 다르면 Toss API 호출 전에 차단되는지 검증한다. */
    @Test
    void confirmRejectsAmountMismatchBeforeCallingToss() {
        Payment payment = payment("order-2", 1L, 10000, 1000, "READY");
        when(paymentMapper.findByOrderId("order-2")).thenReturn(payment);

        assertThatThrownBy(() -> service.confirm(1L, new TossPaymentConfirmRequest("pay-key", "order-2", 9000)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(tossPaymentClient, never()).confirm(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(paymentMapper, never()).increaseUserCredit(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    /** 이미 승인된 결제 건은 Toss API 재호출 없이 중복 처리로 차단되는지 검증한다. */
    @Test
    void confirmRejectsAlreadyPaidPayment() {
        Payment payment = payment("order-3", 1L, 10000, 1000, "PAID");
        when(paymentMapper.findByOrderId("order-3")).thenReturn(payment);

        assertThatThrownBy(() -> service.confirm(1L, new TossPaymentConfirmRequest("pay-key", "order-3", 10000)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(tossPaymentClient, never()).confirm(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    /** 테스트에서 사용할 Toss 리다이렉트 설정 객체를 만든다. */
    private static TossPaymentProperties tossProperties() {
        TossPaymentProperties properties = new TossPaymentProperties();
        properties.setSuccessUrl("http://localhost:5173/billing/success");
        properties.setFailUrl("http://localhost:5173/billing/fail");
        return properties;
    }

    /** 결제 준비 테스트에 사용할 크레딧 상품 더미를 만든다. */
    private static CreditProduct product(String code, String name, int price, int creditAmount) {
        CreditProduct product = new CreditProduct();
        product.setCode(code);
        product.setName(name);
        product.setPrice(price);
        product.setCreditAmount(creditAmount);
        product.setEnabled(true);
        return product;
    }

    /** 결제 승인 테스트에 사용할 payment 더미를 만든다. */
    private static Payment payment(String orderId, Long userId, int amount, int creditAmount, String status) {
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setProvider("TOSS");
        payment.setProductCode("CREDIT_1000");
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setCreditAmount(creditAmount);
        payment.setStatus(status);
        return payment;
    }
}
