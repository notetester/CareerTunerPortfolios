package com.careertuner.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.billing.domain.RefundRequest;
import com.careertuner.billing.domain.RefundPolicy;
import com.careertuner.billing.domain.BenefitTransaction;
import com.careertuner.billing.domain.UserBenefitBalance;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.dto.RefundEligibilityRequest;
import com.careertuner.billing.dto.RefundEligibilityResponse;
import com.careertuner.billing.dto.RefundRequestCreateRequest;
import com.careertuner.billing.dto.RefundRequestResponse;
import com.careertuner.billing.dto.RefundReviewRequest;
import com.careertuner.billing.mapper.RefundRequestMapper;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.domain.CreditTransaction;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.payment.domain.Payment;

import tools.jackson.databind.ObjectMapper;

class RefundRequestServiceTest {
    private final RefundRequestMapper mapper = org.mockito.Mockito.mock(RefundRequestMapper.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final RefundPolicyService refundPolicyService = org.mockito.Mockito.mock(RefundPolicyService.class);
    private final RefundRequestService service = new RefundRequestService(
            mapper, new ObjectMapper(), notificationService, refundPolicyService);

    @Test
    void creditUsageAfterCreditPurchaseIsIneligible() {
        Payment payment = payment("CREDIT");
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.existsCreditUsageAfter(1L, payment.getPaidAt())).thenReturn(true);
        RefundEligibilityResponse result = service.preview(
                1L, new RefundEligibilityRequest(10L, "CHANGE_OF_MIND"));

        assertThat(result.eligibilityResult()).isEqualTo("INELIGIBLE");
        assertThat(result.decisionCode()).isEqualTo("CREDIT_USED");
        assertThat(result.creditUsed()).isTrue();
        assertThat(result.message()).contains("크레딧 사용 이력");
        verify(mapper, never()).insert(any());
    }

    @Test
    void subscriptionBenefitUsageIsIneligible() {
        Payment payment = payment("SUBSCRIPTION");
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.existsBenefitUsageAfter(1L, payment.getPaidAt())).thenReturn(true);
        RefundEligibilityResponse result = service.preview(
                1L, new RefundEligibilityRequest(10L, "CHANGE_OF_MIND"));

        assertThat(result.eligibilityResult()).isEqualTo("INELIGIBLE");
        assertThat(result.decisionCode()).isEqualTo("BENEFIT_USED");
        assertThat(result.benefitUsed()).isTrue();
        assertThat(result.message()).contains("사용권 사용 이력");
    }

    @Test
    void duplicatePaymentReasonRequiresManualReviewEvenWhenUsed() {
        Payment payment = payment("CREDIT");
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.existsCreditUsageAfter(1L, payment.getPaidAt())).thenReturn(true);
        when(mapper.findResponseById(any())).thenReturn(response("REQUESTED", "REVIEW_REQUIRED"));
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<RefundRequest>getArgument(0).setId(100L);
            return null;
        }).when(mapper).insert(any());

        service.create(1L, new RefundRequestCreateRequest(10L, "DUPLICATE_PAYMENT", "같은 상품이 두 번 결제됨"));

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getEligibilityResult()).isEqualTo("REVIEW_REQUIRED");
    }

    @Test
    void oldPaymentWithoutSnapshotUsesCurrentPolicyAndExplainsExpiry() {
        Payment payment = payment("CREDIT");
        payment.setPaidAt(LocalDateTime.now().minusDays(10));
        payment.setPolicySnapshotJson(null);
        RefundPolicy current = new RefundPolicy();
        current.setId(2L);
        current.setVersion(2);
        current.setTitle("환불 정책");
        current.setSummary("7일 이내 미사용 결제만 환불 가능");
        current.setRulesJson("{\"withdrawalDays\":7,\"usedPolicy\":\"NO_REFUND\"}");
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(refundPolicyService.currentPolicy()).thenReturn(current);

        RefundEligibilityResponse result = service.preview(
                1L, new RefundEligibilityRequest(10L, "CHANGE_OF_MIND"));

        assertThat(result.eligibilityResult()).isEqualTo("INELIGIBLE");
        assertThat(result.decisionCode()).isEqualTo("WITHDRAWAL_PERIOD_EXPIRED");
        assertThat(result.message()).contains("10일").contains("7일");
        assertThat(result.policyVersion()).isEqualTo(2);
    }

    @Test
    void ineligibleRequestIsRejectedWithPolicyMessageInsteadOfBeingInserted() {
        Payment payment = payment("CREDIT");
        payment.setPaidAt(LocalDateTime.now().minusDays(10));
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);

        assertThatThrownBy(() -> service.create(
                1L, new RefundRequestCreateRequest(10L, "CHANGE_OF_MIND", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED))
                .hasMessageContaining("환불 신청 기간");
        verify(mapper, never()).insert(any());
    }

    @Test
    void approvalMarksPaymentRefundedBeforeCompletingRequest() {
        RefundRequestResponse current = response("REQUESTED", "ELIGIBLE");
        RefundRequestResponse approved = response("APPROVED", "ELIGIBLE");
        Payment payment = payment("CREDIT");
        when(mapper.findResponseById(100L)).thenReturn(current, approved);
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.deductUserCreditForRefund(1L, 10)).thenReturn(1);
        when(mapper.findUserCredit(1L)).thenReturn(1_000);
        when(mapper.markPaymentRefunded(10L)).thenReturn(1);
        when(mapper.approve(100L, 9L, "미사용 확인")).thenReturn(1);

        RefundRequestResponse result = service.approve(
                new AuthUser(9L, "super-admin@careertuner.dev", "SUPER_ADMIN"),
                100L,
                new RefundReviewRequest("미사용 확인"));

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(mapper).deductUserCreditForRefund(1L, 10);
        ArgumentCaptor<CreditTransaction> transactionCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(mapper).insertCreditTransaction(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getType()).isEqualTo("REFUND");
        assertThat(transactionCaptor.getValue().getAmount()).isEqualTo(-10);
        assertThat(transactionCaptor.getValue().getBalanceAfter()).isEqualTo(1_000);
        verify(mapper).markPaymentRefunded(10L);
        verify(mapper).approve(100L, 9L, "미사용 확인");
        verify(notificationService).notify(any());
    }

    @Test
    void approvalRejectsCreditRefundWhenCreditWasUsedAfterRequest() {
        RefundRequestResponse current = response("REQUESTED", "ELIGIBLE");
        Payment payment = payment("CREDIT");
        when(mapper.findResponseById(100L)).thenReturn(current);
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.existsCreditUsageAfter(1L, payment.getPaidAt())).thenReturn(true);

        assertThatThrownBy(() -> service.approve(
                new AuthUser(9L, "super-admin@careertuner.dev", "SUPER_ADMIN"),
                100L,
                new RefundReviewRequest("미사용 확인")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED))
                .hasMessageContaining("크레딧 사용 이력");

        verify(mapper, never()).deductUserCreditForRefund(any(), any(Integer.class));
        verify(mapper, never()).markPaymentRefunded(any());
        verify(mapper, never()).approve(any(), any(), any());
    }

    @Test
    void approvalRejectsCreditRefundWhenBalanceCannotCoverPurchasedCredits() {
        RefundRequestResponse current = response("REQUESTED", "ELIGIBLE");
        Payment payment = payment("CREDIT");
        when(mapper.findResponseById(100L)).thenReturn(current);
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.deductUserCreditForRefund(1L, 10)).thenReturn(0);

        assertThatThrownBy(() -> service.approve(
                new AuthUser(9L, "super-admin@careertuner.dev", "SUPER_ADMIN"),
                100L,
                new RefundReviewRequest("미사용 확인")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("잔액이 부족");

        verify(mapper, never()).insertCreditTransaction(any());
        verify(mapper, never()).markPaymentRefunded(any());
        verify(mapper, never()).approve(any(), any(), any());
    }

    @Test
    void approvalImmediatelyRevokesUnusedSubscriptionAndBenefits() {
        RefundRequestResponse current = subscriptionResponse("REQUESTED", "ELIGIBLE");
        RefundRequestResponse approved = subscriptionResponse("APPROVED", "ELIGIBLE");
        Payment payment = payment("SUBSCRIPTION");
        payment.setPlan("BASIC");
        payment.setCreditAmount(0);
        UserSubscription subscription = subscription(payment);
        UserBenefitBalance balance = benefitBalance(subscription, 2, 0, 2);

        when(mapper.findResponseById(100L)).thenReturn(current, approved);
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.findSubscriptionForRefund(10L, 1L, "BASIC", payment.getPaidAt())).thenReturn(subscription);
        when(mapper.findBenefitBalancesForRefund(1L, subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd()))
                .thenReturn(List.of(balance));
        when(mapper.markSubscriptionRefunded(200L)).thenReturn(1);
        when(mapper.revokeBenefitBalanceIfUnused(300L)).thenReturn(1);
        when(mapper.markPaymentRefunded(10L)).thenReturn(1);
        when(mapper.approve(100L, 9L, "미사용 확인")).thenReturn(1);

        RefundRequestResponse result = service.approve(
                new AuthUser(9L, "super-admin@careertuner.dev", "SUPER_ADMIN"),
                100L,
                new RefundReviewRequest("미사용 확인"));

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(mapper).markSubscriptionRefunded(200L);
        verify(mapper).revokeBenefitBalanceIfUnused(300L);
        verify(mapper).resetUserPlanAfterSubscriptionRefund(eq(1L), eq(200L), any());
        ArgumentCaptor<BenefitTransaction> transactionCaptor = ArgumentCaptor.forClass(BenefitTransaction.class);
        verify(mapper).insertBenefitTransaction(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getTransactionType()).isEqualTo("REFUND_REVOKE");
        assertThat(transactionCaptor.getValue().getAmount()).isEqualTo(-2);
        assertThat(transactionCaptor.getValue().getBalanceAfter()).isZero();
    }

    @Test
    void approvalRejectsSubscriptionRefundWhenBenefitWasUsedAfterRequest() {
        RefundRequestResponse current = subscriptionResponse("REQUESTED", "ELIGIBLE");
        Payment payment = payment("SUBSCRIPTION");
        payment.setPlan("BASIC");
        when(mapper.findResponseById(100L)).thenReturn(current);
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.existsBenefitUsageAfter(1L, payment.getPaidAt())).thenReturn(true);

        assertThatThrownBy(() -> service.approve(
                new AuthUser(9L, "super-admin@careertuner.dev", "SUPER_ADMIN"),
                100L,
                new RefundReviewRequest("미사용 확인")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED))
                .hasMessageContaining("사용권 사용 이력");

        verify(mapper, never()).markSubscriptionRefunded(any());
        verify(mapper, never()).markPaymentRefunded(any());
    }

    private Payment payment(String productType) {
        Payment payment = new Payment();
        payment.setId(10L);
        payment.setUserId(1L);
        payment.setProductType(productType);
        payment.setProductCode("PRODUCT");
        payment.setOrderId("ORDER-10");
        payment.setAmount(9_900);
        payment.setCreditAmount(10);
        payment.setStatus("PAID");
        payment.setPaidAt(LocalDateTime.now().minusDays(1));
        payment.setPolicySnapshotJson("{\"refundPolicy\":{\"id\":1,\"version\":2,\"rules\":{\"withdrawalDays\":7,\"usedPolicy\":\"NO_REFUND\"}}}");
        return payment;
    }

    private RefundRequestResponse response(String status, String eligibility) {
        return new RefundRequestResponse(
                100L, 10L, 1L, "user@careertuner.dev", "사용자", "ORDER-10",
                "CREDIT", "PRODUCT", null, 9_900, LocalDateTime.now().minusDays(1),
                "PAID", status, "CHANGE_OF_MIND", null, eligibility, false, false,
                9_900, "{}", null, null, LocalDateTime.now(), null);
    }

    private RefundRequestResponse subscriptionResponse(String status, String eligibility) {
        return new RefundRequestResponse(
                100L, 10L, 1L, "user@careertuner.dev", "사용자", "ORDER-10",
                "SUBSCRIPTION", "BASIC", "BASIC", 9_900, LocalDateTime.now().minusDays(1),
                "PAID", status, "CHANGE_OF_MIND", null, eligibility, false, false,
                9_900, "{}", null, null, LocalDateTime.now(), null);
    }

    private UserSubscription subscription(Payment payment) {
        return UserSubscription.builder()
                .id(200L)
                .paymentId(payment.getId())
                .userId(payment.getUserId())
                .planCode("BASIC")
                .status("ACTIVE")
                .startedAt(payment.getPaidAt())
                .currentPeriodStart(payment.getPaidAt())
                .currentPeriodEnd(payment.getPaidAt().plusMonths(1))
                .build();
    }

    private UserBenefitBalance benefitBalance(UserSubscription subscription, int granted, int used, int remaining) {
        UserBenefitBalance balance = new UserBenefitBalance();
        balance.setId(300L);
        balance.setUserId(subscription.getUserId());
        balance.setBenefitCode("CORRECTION");
        balance.setPeriodStart(subscription.getCurrentPeriodStart());
        balance.setPeriodEnd(subscription.getCurrentPeriodEnd());
        balance.setGrantedQuantity(granted);
        balance.setUsedQuantity(used);
        balance.setRemainingQuantity(remaining);
        balance.setSourcePlanCode(subscription.getPlanCode());
        return balance;
    }
}
