package com.careertuner.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.billing.domain.RefundRequest;
import com.careertuner.billing.dto.RefundRequestCreateRequest;
import com.careertuner.billing.dto.RefundRequestResponse;
import com.careertuner.billing.dto.RefundReviewRequest;
import com.careertuner.billing.mapper.RefundRequestMapper;
import com.careertuner.common.security.AuthUser;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.payment.domain.Payment;

import tools.jackson.databind.ObjectMapper;

class RefundRequestServiceTest {
    private final RefundRequestMapper mapper = org.mockito.Mockito.mock(RefundRequestMapper.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final RefundRequestService service = new RefundRequestService(mapper, new ObjectMapper(), notificationService);

    @Test
    void creditUsageAfterCreditPurchaseIsIneligible() {
        Payment payment = payment("CREDIT");
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.existsCreditUsageAfter(1L, payment.getPaidAt())).thenReturn(true);
        when(mapper.findResponseById(any())).thenReturn(response("REQUESTED", "INELIGIBLE"));
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<RefundRequest>getArgument(0).setId(100L);
            return null;
        }).when(mapper).insert(any());

        service.create(1L, new RefundRequestCreateRequest(10L, "CHANGE_OF_MIND", null));

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getEligibilityResult()).isEqualTo("INELIGIBLE");
        assertThat(captor.getValue().isCreditUsed()).isTrue();
        assertThat(captor.getValue().isBenefitUsed()).isFalse();
    }

    @Test
    void subscriptionBenefitUsageIsIneligible() {
        Payment payment = payment("SUBSCRIPTION");
        when(mapper.findOwnedPayment(10L, 1L)).thenReturn(payment);
        when(mapper.existsBenefitUsageAfter(1L, payment.getPaidAt())).thenReturn(true);
        when(mapper.findResponseById(any())).thenReturn(response("REQUESTED", "INELIGIBLE"));
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<RefundRequest>getArgument(0).setId(100L);
            return null;
        }).when(mapper).insert(any());

        service.create(1L, new RefundRequestCreateRequest(10L, "CHANGE_OF_MIND", null));

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getEligibilityResult()).isEqualTo("INELIGIBLE");
        assertThat(captor.getValue().isBenefitUsed()).isTrue();
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
    void approvalMarksPaymentRefundedBeforeCompletingRequest() {
        RefundRequestResponse current = response("REQUESTED", "ELIGIBLE");
        RefundRequestResponse approved = response("APPROVED", "ELIGIBLE");
        when(mapper.findResponseById(100L)).thenReturn(current, approved);
        when(mapper.markPaymentRefunded(10L)).thenReturn(1);
        when(mapper.approve(100L, 9L, "미사용 확인")).thenReturn(1);

        RefundRequestResponse result = service.approve(
                new AuthUser(9L, "super-admin@careertuner.dev", "SUPER_ADMIN"),
                100L,
                new RefundReviewRequest("미사용 확인"));

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(mapper).markPaymentRefunded(10L);
        verify(mapper).approve(100L, 9L, "미사용 확인");
        verify(notificationService).notify(any());
    }

    private Payment payment(String productType) {
        Payment payment = new Payment();
        payment.setId(10L);
        payment.setUserId(1L);
        payment.setProductType(productType);
        payment.setProductCode("PRODUCT");
        payment.setOrderId("ORDER-10");
        payment.setAmount(9_900);
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
}
