package com.careertuner.billing.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import com.careertuner.billing.dto.AiChargePreviewRequest;
import com.careertuner.billing.dto.AiChargePreviewResponse;
import com.careertuner.billing.service.AiChargePreviewService;
import com.careertuner.billing.service.RefundPolicyService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

class AiChargePreflightTest {

    private final AiChargePreviewService previewService = mock(AiChargePreviewService.class);
    private final RefundPolicyService refundPolicyService = mock(RefundPolicyService.class);
    private final AiChargePreflightService service =
            new AiChargePreflightService(previewService, refundPolicyService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void interceptorRejectsMissingAcknowledgementBeforeController() throws Exception {
        AiChargePreflightService preflight = mock(AiChargePreflightService.class);
        AiChargePreflightInterceptor interceptor = new AiChargePreflightInterceptor(preflight);
        authenticate();

        assertThatThrownBy(() -> interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/interview/questions/1/answers"),
                new MockHttpServletResponse(), handler("charged")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(preflight, never()).requireAcknowledged(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void interceptorAcceptsOnlyMatchingFeatureAndForwardsActionKey() throws Exception {
        AiChargePreflightService preflight = mock(AiChargePreflightService.class);
        AiChargePreflightInterceptor interceptor = new AiChargePreflightInterceptor(preflight);
        authenticate();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/interview/questions/1/answers");
        request.addHeader("X-AI-Charge-Feature", "INTERVIEW_ANSWER_EVAL");
        request.addHeader("X-AI-Charge-Acknowledgement", "AI_USAGE:uuid");

        assertThat(interceptor.preHandle(
                request, new MockHttpServletResponse(), handler("charged"))).isTrue();
        verify(preflight).requireAcknowledged(
                7L, "INTERVIEW_ANSWER_EVAL", "AI_USAGE:uuid");
    }

    @Test
    void serviceChecksCurrentPreviewAndRefundAcknowledgement() {
        when(previewService.preview(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(AiChargePreviewRequest.class)))
                .thenReturn(preview("CREDIT", true, "CREDIT_USE"));

        service.requireAcknowledged(7L, "INTERVIEW_ANSWER_EVAL", "AI_USAGE:key");

        verify(refundPolicyService).requireUsageAcknowledgement(
                7L, "CREDIT_USE", "AI_USAGE:key");
    }

    @Test
    void serviceRejectsInsufficientBalanceBeforeAiWork() {
        when(previewService.preview(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(AiChargePreviewRequest.class)))
                .thenReturn(preview("CREDIT", false, "CREDIT_USE"));

        assertThatThrownBy(() -> service.requireAcknowledged(
                7L, "INTERVIEW_ANSWER_EVAL", "AI_USAGE:key"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_CREDIT);
        verify(refundPolicyService, never()).requireUsageAcknowledgement(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private void authenticate() {
        AuthUser user = new AuthUser(7L, "user@example.com", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    private HandlerMethod handler(String name) throws Exception {
        DummyController controller = new DummyController();
        Method method = DummyController.class.getDeclaredMethod(name);
        return new HandlerMethod(controller, method);
    }

    private AiChargePreviewResponse preview(String chargeType, boolean sufficient, String trigger) {
        return new AiChargePreviewResponse(
                "INTERVIEW_ANSWER_EVAL", chargeType, null,
                10, 10, 10, 1000, false,
                0, 100, sufficient, trigger, "AI_USAGE:key",
                1L, 1, "환불 정책", "요약", "{}");
    }

    private static class DummyController {
        @RequiresAiCharge("INTERVIEW_ANSWER_EVAL")
        void charged() {}
    }
}
