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
        props.getApp().setFrontendUrl("https://careertuner.example.com/");
        props.getApp().setSitesFrontendUrl("https://sites.example.com/");
        resolver = new FrontendReturnUrlResolver(props);
    }

    @Test
    void missingHeaderUsesPrimaryFrontend() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThat(resolver.resolve(request))
                .isEqualTo(new FrontendReturnTarget("primary", "https://careertuner.example.com"));
    }

    @Test
    void sitesHeaderSelectsConfiguredSitesFrontend() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(FrontendReturnUrlResolver.FRONTEND_CLIENT_HEADER)).thenReturn("sites");

        FrontendReturnTarget target = resolver.resolve(request);

        assertThat(target.client()).isEqualTo("sites");
        assertThat(target.absoluteUrl("/auth/callback"))
                .isEqualTo("https://sites.example.com/auth/callback");
    }

    @Test
    void nativeHeaderIsNamedButGenericLinksUseCanonicalHttpsFrontend() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(FrontendReturnUrlResolver.FRONTEND_CLIENT_HEADER)).thenReturn("native");

        FrontendReturnTarget target = resolver.resolve(request);

        assertThat(target.client()).isEqualTo("native");
        assertThat(target.absoluteUrl("/auth/reset-password?token=opaque"))
                .isEqualTo("https://careertuner.example.com/auth/reset-password?token=opaque");
        assertThat(target.baseUrl()).startsWith("https://");
    }

    @Test
    void storedNativeClientFallsBackToPrimaryForEmailCallback() {
        assertThat(resolver.resolveStoredClient("native"))
                .isEqualTo(new FrontendReturnTarget("primary", "https://careertuner.example.com"));
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
