package com.careertuner.consent.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import com.careertuner.ai.autoprep.AutoPrepController;
import com.careertuner.ai.autoprep.AutoPrepIntakeService;
import com.careertuner.ai.autoprep.AutoPrepOrchestrator;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.service.ConsentService;

class ConsentPolicyInterceptorTest {

    private static final AuthUser USER = new AuthUser(7L, "user@example.com", "USER");

    private final ConsentService consentService = mock(ConsentService.class);
    private final ConsentPolicyInterceptor interceptor = new ConsentPolicyInterceptor(consentService);
    private final TestController controller = new TestController();

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER, null, java.util.List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksMemberApiWhenRequiredConsentWasWithdrawn() throws Exception {
        when(consentService.hasRequiredConsents(7L)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(
                request("GET", "/api/dashboard"), new MockHttpServletResponse(), handler("normal")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONSENT_REQUIRED);
    }

    @Test
    void allowsLegalAndConsentRecoveryApisWithoutRequiredConsent() throws Exception {
        interceptor.preHandle(
                request("GET", "/api/legal/terms"), new MockHttpServletResponse(), handler("normal"));

        verify(consentService, never()).hasRequiredConsents(7L);
    }

    @Test
    void blocksAnnotatedAiFeatureWhenAiConsentIsMissing() throws Exception {
        when(consentService.hasRequiredConsents(7L)).thenReturn(true);
        when(consentService.hasCurrentConsent(7L, ConsentType.AI_DATA)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(
                request("POST", "/api/corrections"), new MockHttpServletResponse(), handler("ai")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 데이터 이용 동의");
    }

    @Test
    void requiresBothAiAndResumeConsentForResumeAnalysis() throws Exception {
        when(consentService.hasRequiredConsents(7L)).thenReturn(true);
        when(consentService.hasCurrentConsent(7L, ConsentType.AI_DATA)).thenReturn(true);
        when(consentService.hasCurrentConsent(7L, ConsentType.RESUME_ANALYSIS)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(
                request("POST", "/api/profile/import/analyze"), new MockHttpServletResponse(), handler("resume")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이력서 분석 개인정보");
    }

    @Test
    void autoPrepExecutionRequiresResumeConsentBeforePortfolioEvidenceProcessing() throws Exception {
        when(consentService.hasRequiredConsents(7L)).thenReturn(true);
        when(consentService.hasCurrentConsent(7L, ConsentType.AI_DATA)).thenReturn(true);
        when(consentService.hasCurrentConsent(7L, ConsentType.RESUME_ANALYSIS)).thenReturn(false);

        AutoPrepController autoPrepController = new AutoPrepController(
                mock(AutoPrepOrchestrator.class), mock(AutoPrepIntakeService.class));
        HandlerMethod autoPrepRun = new HandlerMethod(
                autoPrepController,
                AutoPrepController.class.getMethod("run", AuthUser.class, AutoPrepRequest.class));
        HandlerMethod autoPrepStream = new HandlerMethod(
                autoPrepController,
                AutoPrepController.class.getMethod("runStream", AuthUser.class, AutoPrepRequest.class));

        assertThatThrownBy(() -> interceptor.preHandle(
                request("POST", "/api/auto-prep/run"), new MockHttpServletResponse(), autoPrepRun))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONSENT_REQUIRED);
        assertThatThrownBy(() -> interceptor.preHandle(
                request("POST", "/api/auto-prep/run/stream"), new MockHttpServletResponse(), autoPrepStream))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONSENT_REQUIRED);
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    private HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(controller, TestController.class.getMethod(name));
    }

    static class TestController {
        public void normal() {
        }

        @RequiresConsent(ConsentType.AI_DATA)
        public void ai() {
        }

        @RequiresConsent({ConsentType.AI_DATA, ConsentType.RESUME_ANALYSIS})
        public void resume() {
        }
    }
}
