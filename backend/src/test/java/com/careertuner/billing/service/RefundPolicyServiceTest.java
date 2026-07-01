package com.careertuner.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.notice.dto.AdminNoticeRequest;
import com.careertuner.admin.notice.dto.AdminNoticeResponse;
import com.careertuner.admin.notice.service.AdminNoticeService;
import com.careertuner.billing.domain.RefundPolicy;
import com.careertuner.billing.dto.AdminRefundPolicySaveRequest;
import com.careertuner.billing.mapper.RefundPolicyMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import tools.jackson.databind.ObjectMapper;

class RefundPolicyServiceTest {

    private static final AuthUser ADMIN = new AuthUser(1L, "admin@careertuner.dev", "ADMIN");

    private final RefundPolicyMapper mapper = org.mockito.Mockito.mock(RefundPolicyMapper.class);
    private final AdminNoticeService noticeService = org.mockito.Mockito.mock(AdminNoticeService.class);
    private final RefundPolicyService service = new RefundPolicyService(mapper, noticeService, new ObjectMapper());

    @Test
    void saveDraftRejectsWithdrawalPeriodBelowLegalMinimum() {
        AdminRefundPolicySaveRequest request = new AdminRefundPolicySaveRequest(
                "환불 정책",
                "요약",
                "본문",
                rules(3),
                false,
                LocalDateTime.now());

        assertThatThrownBy(() -> service.saveDraft(ADMIN, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(mapper, never()).insert(any());
    }

    @Test
    void publishCreatesPinnedNoticeAndUnpinsPreviousRefundNotice() {
        RefundPolicy draft = policy(2L, "DRAFT", 2);
        RefundPolicy published = policy(2L, "PUBLISHED", 2);
        published.setNoticeId(99L);

        AdminNoticeResponse previous = AdminNoticeResponse.builder()
                .id(10L)
                .title("이전 환불 정책")
                .content("이전 내용")
                .category("REFUND_POLICY")
                .status("PUBLISHED")
                .pinned(true)
                .build();
        when(mapper.findById(2L)).thenReturn(draft, published);
        when(noticeService.getNotices(ADMIN)).thenReturn(List.of(previous));
        when(noticeService.createNotice(any(), any()))
                .thenReturn(AdminNoticeResponse.builder().id(99L).build());
        when(mapper.publishDraft(any(), any(), any())).thenReturn(1);

        var result = service.publish(ADMIN, 2L);

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.noticeId()).isEqualTo(99L);
        verify(noticeService).updateNotice(
                org.mockito.ArgumentMatchers.eq(ADMIN),
                org.mockito.ArgumentMatchers.eq(10L),
                any(AdminNoticeRequest.class));
        verify(noticeService).createNotice(
                org.mockito.ArgumentMatchers.eq(ADMIN),
                any(AdminNoticeRequest.class));
        verify(mapper).publishDraft(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(99L),
                any(LocalDateTime.class));
    }

    @Test
    void publishDoesNotChangePolicyWhenNoticeCreationFails() {
        RefundPolicy draft = policy(2L, "DRAFT", 2);
        when(mapper.findById(2L)).thenReturn(draft);
        when(noticeService.getNotices(ADMIN)).thenReturn(List.of());
        when(noticeService.createNotice(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "notice failed"));

        assertThatThrownBy(() -> service.publish(ADMIN, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("notice failed");

        verify(mapper, never()).publishDraft(any(), any(), any());
    }

    @Test
    void acknowledgementRejectsStalePolicyVersion() {
        RefundPolicy current = policy(3L, "PUBLISHED", 3);
        when(mapper.findCurrent(any(), any())).thenReturn(current);

        assertThatThrownBy(() -> service.acknowledge(7L, 2L, "PAYMENT", "payment-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(mapper, never()).insertAcknowledgement(any(), any(), any(), any());
    }

    @Test
    void paymentSnapshotContainsImmutableRefundPolicyVersion() {
        RefundPolicy policy = policy(3L, "PUBLISHED", 3);

        String snapshot = service.appendPaymentSnapshot(
                "{\"code\":\"CREDIT_100\"}", policy, "payment-1");

        assertThat(snapshot).contains("\"refundPolicy\"");
        assertThat(snapshot).contains("\"version\":3");
        assertThat(snapshot).contains("\"policyCode\":\"REFUND_DEFAULT\"");
        assertThat(snapshot).contains("\"acknowledgementKey\":\"payment-1\"");
    }

    private static RefundPolicy policy(Long id, String status, int version) {
        RefundPolicy policy = new RefundPolicy();
        policy.setId(id);
        policy.setPolicyCode("REFUND_DEFAULT");
        policy.setVersion(version);
        policy.setTitle("환불 정책");
        policy.setSummary("변경 요약");
        policy.setContent("환불 정책 본문");
        policy.setRulesJson("""
                {"legalBasis":"E_COMMERCE_ACT","withdrawalDays":7,"unusedPolicy":"FULL_REFUND",
                "usedPolicy":"MANUAL_REVIEW","exceptionCodes":["SYSTEM_ERROR"],
                "noticeScopes":["PAYMENT","CREDIT_USE","BENEFIT_USE"]}
                """);
        policy.setStatus(status);
        policy.setEffectiveAt(LocalDateTime.now().minusMinutes(1));
        return policy;
    }

    private static Map<String, Object> rules(int days) {
        return Map.of(
                "legalBasis", "E_COMMERCE_ACT",
                "withdrawalDays", days,
                "unusedPolicy", "FULL_REFUND",
                "usedPolicy", "MANUAL_REVIEW",
                "exceptionCodes", List.of("SYSTEM_ERROR"),
                "noticeScopes", List.of("PAYMENT", "CREDIT_USE", "BENEFIT_USE"));
    }
}
