package com.careertuner.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.exception.BusinessException;

import jakarta.servlet.http.HttpServletRequest;

class FrontendReturnUrlResolverTest {

    private final CareerTunerProperties props = new CareerTunerProperties();
    private FrontendReturnUrlResolver resolver;

    @BeforeEach
    void setUp() {
        props.getApp().setFrontendUrl("https://careertuner.kro.kr/");
        props.getApp().setSitesFrontendUrl("https://careertuner.career-tuner-4654.chatgpt.site/");
        resolver = new FrontendReturnUrlResolver(props);
    }

    @Test
    void missingHeaderUsesPrimaryFrontend() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThat(resolver.resolve(request))
                .isEqualTo(new FrontendReturnTarget("primary", "https://careertuner.kro.kr"));
    }

    @Test
    void sitesHeaderSelectsConfiguredSitesFrontend() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(FrontendReturnUrlResolver.FRONTEND_CLIENT_HEADER)).thenReturn("sites");

        FrontendReturnTarget target = resolver.resolve(request);

        assertThat(target.client()).isEqualTo("sites");
        assertThat(target.absoluteUrl("/auth/callback"))
                .isEqualTo("https://careertuner.career-tuner-4654.chatgpt.site/auth/callback");
    }

    @Test
    void unknownRequestClientIsRejectedInsteadOfAcceptingAnOrigin() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(FrontendReturnUrlResolver.FRONTEND_CLIENT_HEADER))
                .thenReturn("https://evil.example");

        assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void requestClientMustMatchExactLowercaseKey() {
        assertThatThrownBy(() -> resolver.resolveClient("Sites")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resolver.resolveClient("sites ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resolver.resolveClient("sites\r\n")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resolver.resolveClient("")).isInstanceOf(BusinessException.class);
    }

    @Test
    void unknownStoredClientFallsBackToPrimaryForLegacyRows() {
        assertThat(resolver.resolveStoredClient("legacy").client()).isEqualTo("primary");
    }

    @Test
    void nonHttpsRemoteOriginIsRejected() {
        props.getApp().setSitesFrontendUrl("http://example.com");

        assertThatThrownBy(() -> resolver.resolveClient("sites")).isInstanceOf(BusinessException.class);
    }
}
