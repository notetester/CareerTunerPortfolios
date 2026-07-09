package com.careertuner.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.domain.RefundPolicy;
import com.careertuner.billing.service.BillingPolicyService;
import com.careertuner.billing.service.BillingService;
import com.careertuner.billing.service.RefundPolicyService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.domain.CreditProduct;
import com.careertuner.payment.domain.Payment;
import com.careertuner.payment.dto.TossPaymentConfirmRequest;
import com.careertuner.payment.dto.TossPaymentConfirmResponse;
import com.careertuner.payment.dto.TossPaymentReadyRequest;
import com.careertuner.payment.dto.TossPaymentReadyResponse;
import com.careertuner.payment.mapper.PaymentMapper;
import com.careertuner.payment.service.TossPaymentClient.ConfirmedPayment;

class PaymentServiceImplTest {

    private final BillingService billingService = org.mockito.Mockito.mock(BillingService.class);
    private final BillingPolicyService billingPolicyService = org.mockito.Mockito.mock(BillingPolicyService.class);
    private final RefundPolicyService refundPolicyService = org.mockito.Mockito.mock(RefundPolicyService.class);
    private final PaymentMapper paymentMapper = org.mockito.Mockito.mock(PaymentMapper.class);
    private final TossPaymentClient tossPaymentClient = org.mockito.Mockito.mock(TossPaymentClient.class);
    private final TossPaymentProperties properties = tossProperties();
    private final com.careertuner.reward.service.RewardService rewardService =
            org.mockito.Mockito.mock(com.careertuner.reward.service.RewardService.class);
    private final PaymentServiceImpl service = new PaymentServiceImpl(
            billingService,
            billingPolicyService,
            refundPolicyService,
            paymentMapper,
            tossPaymentClient,
            properties,
            rewardService);

    @Test
    void readyCreatesPendingTossPaymentFromCreditProductSnapshot() {
        CreditProduct product = product("CREDIT_1000", "Credit 1000", 10000, 1000);
        RefundPolicy refundPolicy = refundPolicy(1L);
        when(billingPolicyService.enabledCreditProductByCode("CREDIT_1000")).thenReturn(product);
        when(billingPolicyService.creditProductSnapshotJson(product)).thenReturn("{\"code\":\"CREDIT_1000\"}");
        when(refundPolicyService.requirePaymentAcknowledgement(1L, null, null)).thenReturn(refundPolicy);
        when(refundPolicyService.appendPaymentSnapshot("{\"code\":\"CREDIT_1000\"}", refundPolicy, null))
                .thenReturn("{\"code\":\"CREDIT_1000\",\"refundPolicy\":{\"id\":1}}");

        TossPaymentReadyResponse response = service.ready(
                1L,
                "user@careertuner.dev",
                new TossPaymentReadyRequest("CREDIT_1000"));

        assertThat(response.orderId()).startsWith("CT-");
        assertThat(response.productType()).isEqualTo("CREDIT");
        assertThat(response.productCode()).isEqualTo("CREDIT_1000");
        assertThat(response.planCode()).isNull();
        assertThat(response.orderName()).isEqualTo("Credit 1000");
        assertThat(response.amount()).isEqualTo(10000);
        assertThat(response.creditAmount()).isEqualTo(1000);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getProductType()).isEqualTo("CREDIT");
        assertThat(saved.getProductCode()).isEqualTo("CREDIT_1000");
        assertThat(saved.getPlan()).isNull();
        assertThat(saved.getCreditAmount()).isEqualTo(1000);
        assertThat(saved.getPolicySnapshotJson()).contains("refundPolicy");
        assertThat(saved.getStatus()).isEqualTo("READY");
    }

    @Test
    void readyCreatesPendingTossPaymentFromSubscriptionPlanSnapshot() {
        SubscriptionPlan plan = plan("BASIC", "Basic", 9900);
        RefundPolicy refundPolicy = refundPolicy(1L);
        when(billingPolicyService.activePlanByCode("BASIC")).thenReturn(plan);
        when(billingPolicyService.subscriptionSnapshotJson("BASIC")).thenReturn("{\"plan\":{\"code\":\"BASIC\"}}");
        when(refundPolicyService.requirePaymentAcknowledgement(1L, null, null)).thenReturn(refundPolicy);
        when(refundPolicyService.appendPaymentSnapshot("{\"plan\":{\"code\":\"BASIC\"}}", refundPolicy, null))
                .thenReturn("{\"plan\":{\"code\":\"BASIC\"},\"refundPolicy\":{\"id\":1}}");

        TossPaymentReadyResponse response = service.ready(
                1L,
                "user@careertuner.dev",
                new TossPaymentReadyRequest("SUBSCRIPTION", "BASIC"));

        assertThat(response.productType()).isEqualTo("SUBSCRIPTION");
        assertThat(response.productCode()).isEqualTo("BASIC");
        assertThat(response.planCode()).isEqualTo("BASIC");
        assertThat(response.amount()).isEqualTo(9900);
        assertThat(response.creditAmount()).isZero();

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getProductType()).isEqualTo("SUBSCRIPTION");
        assertThat(saved.getProductCode()).isEqualTo("BASIC");
        assertThat(saved.getPlan()).isEqualTo("BASIC");
        assertThat(saved.getCreditAmount()).isZero();
        assertThat(saved.getPolicySnapshotJson()).contains("refundPolicy");
    }

    @Test
    void confirmApprovesTossPaymentAndIncreasesUserCredit() {
        Payment payment = payment("order-1", 1L, "CREDIT", "CREDIT_1000", null, 10000, 1000, "READY");
        when(paymentMapper.findByOrderId("order-1")).thenReturn(payment);
        when(paymentMapper.existsByPaymentKey("pay-key")).thenReturn(false);
        when(tossPaymentClient.confirm("pay-key", "order-1", 10000))
                .thenReturn(new ConfirmedPayment("pay-key", "order-1", 10000, "DONE"));
        when(paymentMapper.markPaidIfReady("order-1", "pay-key")).thenReturn(1);
        when(billingService.grantCreditsAfterPayment(1L, "CREDIT_1000", 1000)).thenReturn(1500);

        TossPaymentConfirmResponse response = service.confirm(
                1L,
                new TossPaymentConfirmRequest("pay-key", "order-1", 10000));

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.productType()).isEqualTo("CREDIT");
        assertThat(response.balance()).isEqualTo(1500);
        verify(billingService).grantCreditsAfterPayment(1L, "CREDIT_1000", 1000);
        verify(billingService, never()).activateSubscriptionAfterPayment(any(), any(), any(), any());
    }

    @Test
    void confirmApprovesTossPaymentAndActivatesSubscription() {
        Payment payment = payment("order-2", 1L, "SUBSCRIPTION", "BASIC", "BASIC", 9900, 0, "READY");
        payment.setPolicySnapshotJson("{\"plan\":{\"code\":\"BASIC\"}}");
        when(paymentMapper.findByOrderId("order-2")).thenReturn(payment);
        when(paymentMapper.existsByPaymentKey("pay-key")).thenReturn(false);
        when(tossPaymentClient.confirm("pay-key", "order-2", 9900))
                .thenReturn(new ConfirmedPayment("pay-key", "order-2", 9900, "DONE"));
        when(paymentMapper.markPaidIfReady("order-2", "pay-key")).thenReturn(1);
        when(paymentMapper.findUserCredit(1L)).thenReturn(500);

        TossPaymentConfirmResponse response = service.confirm(
                1L,
                new TossPaymentConfirmRequest("pay-key", "order-2", 9900));

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.productType()).isEqualTo("SUBSCRIPTION");
        assertThat(response.planCode()).isEqualTo("BASIC");
        verify(billingService).activateSubscriptionAfterPayment(1L, 20L, "BASIC", "{\"plan\":{\"code\":\"BASIC\"}}");
        verify(billingService, never()).grantCreditsAfterPayment(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void confirmRejectsAmountMismatchBeforeCallingToss() {
        Payment payment = payment("order-3", 1L, "CREDIT", "CREDIT_1000", null, 10000, 1000, "READY");
        when(paymentMapper.findByOrderId("order-3")).thenReturn(payment);

        assertThatThrownBy(() -> service.confirm(1L, new TossPaymentConfirmRequest("pay-key", "order-3", 9000)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(tossPaymentClient, never()).confirm(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(billingService, never()).grantCreditsAfterPayment(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void confirmRejectsAlreadyPaidPayment() {
        Payment payment = payment("order-4", 1L, "CREDIT", "CREDIT_1000", null, 10000, 1000, "PAID");
        when(paymentMapper.findByOrderId("order-4")).thenReturn(payment);

        assertThatThrownBy(() -> service.confirm(1L, new TossPaymentConfirmRequest("pay-key", "order-4", 10000)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(tossPaymentClient, never()).confirm(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static TossPaymentProperties tossProperties() {
        TossPaymentProperties properties = new TossPaymentProperties();
        properties.setSuccessUrl("http://localhost:5173/billing/success");
        properties.setFailUrl("http://localhost:5173/billing/fail");
        return properties;
    }

    private static CreditProduct product(String code, String name, int price, int creditAmount) {
        CreditProduct product = new CreditProduct();
        product.setCode(code);
        product.setName(name);
        product.setPrice(price);
        product.setCreditAmount(creditAmount);
        product.setEnabled(true);
        return product;
    }

    private static SubscriptionPlan plan(String code, String name, int monthlyPrice) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(code);
        plan.setName(name);
        plan.setMonthlyPrice(monthlyPrice);
        plan.setActive(true);
        return plan;
    }

    private static RefundPolicy refundPolicy(Long id) {
        RefundPolicy policy = new RefundPolicy();
        policy.setId(id);
        policy.setPolicyCode("REFUND_DEFAULT");
        policy.setVersion(1);
        policy.setStatus("PUBLISHED");
        return policy;
    }

    private static Payment payment(String orderId,
                                   Long userId,
                                   String productType,
                                   String productCode,
                                   String plan,
                                   int amount,
                                   int creditAmount,
                                   String status) {
        Payment payment = new Payment();
        payment.setId(20L);
        payment.setUserId(userId);
        payment.setProvider("TOSS");
        payment.setProductType(productType);
        payment.setProductCode(productCode);
        payment.setPlan(plan);
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setCreditAmount(creditAmount);
        payment.setStatus(status);
        return payment;
    }
}
