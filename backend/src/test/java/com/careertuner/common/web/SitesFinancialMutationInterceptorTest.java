package com.careertuner.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import com.careertuner.admin.billing.controller.AdminPaymentController;
import com.careertuner.admin.billing.controller.AdminPlanController;
import com.careertuner.admin.billing.controller.AdminRefundPolicyController;
import com.careertuner.admin.billing.controller.AdminRefundRequestController;
import com.careertuner.admin.credit.controller.AdminCreditController;
import com.careertuner.admin.reward.controller.AdminRewardController;
import com.careertuner.auth.controller.AuthController;
import com.careertuner.billing.controller.AiChargePreviewController;
import com.careertuner.billing.controller.BillingController;
import com.careertuner.billing.controller.RefundPolicyController;
import com.careertuner.billing.controller.RefundRequestController;
import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.controller.CreditProductController;
import com.careertuner.payment.controller.PaymentController;
import com.careertuner.reward.controller.RewardController;

class SitesFinancialMutationInterceptorTest {

    private SitesFinancialMutationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        CareerTunerProperties props = new CareerTunerProperties();
        props.getApp().setFrontendUrl("https://careertuner.example.com");
        props.getApp().setSitesFrontendUrl("https://sites.example.com");
        interceptor = new SitesFinancialMutationInterceptor(new FrontendReturnUrlResolver(props));
    }

    @ParameterizedTest(name = "Sites mutation 차단: {0}.{1}")
    @MethodSource("financialHandlers")
    void sitesBlocksEveryMarkedFinancialMutation(Class<?> controllerType, String methodName) {
        MockHttpServletRequest request = sitesRequest("POST", "/any/path");

        assertForbidden(request, handler(controllerType, methodName));
    }

    static Stream<Arguments> financialHandlers() {
        return Stream.of(
                Arguments.of(PaymentController.class, "ready"),
                Arguments.of(PaymentController.class, "confirm"),
                Arguments.of(PaymentController.class, "cancel"),
                Arguments.of(BillingController.class, "cancelSubscription"),
                Arguments.of(RefundRequestController.class, "create"),
                Arguments.of(RewardController.class, "redeem"),
                Arguments.of(AdminPlanController.class, "createPolicyChange"),
                Arguments.of(AdminPlanController.class, "cancelPolicyChange"),
                Arguments.of(AdminRefundRequestController.class, "approve"),
                Arguments.of(AdminRefundRequestController.class, "reject"),
                Arguments.of(AdminRefundPolicyController.class, "saveDraft"),
                Arguments.of(AdminRefundPolicyController.class, "publish"),
                Arguments.of(AdminCreditController.class, "adjust"),
                Arguments.of(AdminRewardController.class, "updateRule"),
                Arguments.of(AdminRewardController.class, "toggleRule"),
                Arguments.of(AdminRewardController.class, "createLevel"),
                Arguments.of(AdminRewardController.class, "updateLevel"),
                Arguments.of(AdminRewardController.class, "deleteLevel"),
                Arguments.of(AdminRewardController.class, "createCoupon"),
                Arguments.of(AdminRewardController.class, "updateCoupon"),
                Arguments.of(AdminRewardController.class, "issueCoupon"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/%70ayments/toss/ready",
            "/api/billing/%2e%2e/payments/toss/confirm",
            "/api//payments/toss/ready"
    })
    void handlerClassificationCannotBeBypassedByEncodedOrNormalizedPath(String requestUri) {
        MockHttpServletRequest request = sitesRequest("POST", requestUri);

        assertForbidden(request, handler(PaymentController.class, "ready"));
    }

    @Test
    void primaryFinancialMutationRemainsAllowed() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments/toss/ready");

        boolean allowed = interceptor.preHandle(
                request, new MockHttpServletResponse(), handler(PaymentController.class, "ready"));

        assertThat(allowed).isTrue();
    }

    @Test
    void sitesCanStillReadFinancialData() {
        MockHttpServletRequest request = sitesRequest("GET", "/api/billing/me");

        boolean allowed = interceptor.preHandle(
                request, new MockHttpServletResponse(), handler(BillingController.class, "me"));

        assertThat(allowed).isTrue();
    }

    @ParameterizedTest(name = "Sites read-only/non-value 허용: {0}.{1} {2}")
    @MethodSource("readOnlyOrNonValueHandlers")
    void sitesAllowsReadOnlyOrNonValueHandlers(
            Class<?> controllerType, String methodName, String httpMethod) {
        MockHttpServletRequest request = sitesRequest(httpMethod, "/read-only-or-preview");

        boolean allowed = interceptor.preHandle(
                request, new MockHttpServletResponse(), handler(controllerType, methodName));

        assertThat(allowed).isTrue();
    }

    static Stream<Arguments> readOnlyOrNonValueHandlers() {
        return Stream.of(
                Arguments.of(AiChargePreviewController.class, "preview", "POST"),
                Arguments.of(RefundRequestController.class, "preview", "POST"),
                Arguments.of(RefundPolicyController.class, "acknowledge", "POST"),
                Arguments.of(CreditProductController.class, "list", "GET"),
                Arguments.of(AdminPaymentController.class, "list", "GET"));
    }

    @Test
    void sitesNonFinancialMutationRemainsAllowed() {
        MockHttpServletRequest request = sitesRequest("POST", "/api/auth/register");

        boolean allowed = interceptor.preHandle(
                request, new MockHttpServletResponse(), handler(AuthController.class, "register"));

        assertThat(allowed).isTrue();
    }

    @Test
    void malformedSitesHeaderDoesNotDowngradeToPrimaryOnFinancialMutation() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments/toss/ready");
        request.addHeader(FrontendReturnUrlResolver.FRONTEND_CLIENT_HEADER, "sites ");

        assertThatThrownBy(() -> interceptor.preHandle(
                request, new MockHttpServletResponse(), handler(PaymentController.class, "ready")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private void assertForbidden(MockHttpServletRequest request, HandlerMethod handler) {
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    private static MockHttpServletRequest sitesRequest(String method, String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.addHeader(FrontendReturnUrlResolver.FRONTEND_CLIENT_HEADER, "sites");
        return request;
    }

    private static HandlerMethod handler(Class<?> controllerType, String methodName) {
        Method method = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return new HandlerMethod(mock(controllerType), method);
    }
}
