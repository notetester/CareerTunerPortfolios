package com.careertuner.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.auth.service.AuthService;
import com.careertuner.auth.service.MfaService;
import com.careertuner.common.web.FrontendReturnTarget;
import com.careertuner.common.web.FrontendReturnUrlResolver;

import jakarta.servlet.http.HttpServletRequest;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final MfaService mfaService = mock(MfaService.class);
    private final FrontendReturnUrlResolver resolver = mock(FrontendReturnUrlResolver.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final AuthController controller = new AuthController(authService, mfaService, resolver);

    private final FrontendReturnTarget primary =
            new FrontendReturnTarget("primary", "https://careertuner.kro.kr");
    private final FrontendReturnTarget sites = new FrontendReturnTarget(
            "sites", "https://careertuner-backup.career-tuner-4654.chatgpt.site");

    @BeforeEach
    void setUp() {
        when(resolver.resolveStoredClient(null)).thenReturn(primary);
        when(resolver.resolveStoredClient("sites")).thenReturn(sites);
    }

    @Test
    void oauthCancellationUsesFrontendClientFromValidStateWithoutRequiringCode() {
        when(authService.resolveOAuthFrontendClient("google", "signed-state")).thenReturn("sites");

        var response = controller.oauthCallback(
                "google", null, "signed-state", "access_denied", request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner-backup.career-tuner-4654.chatgpt.site/auth/callback"
                        + "?error=social_login_cancelled");
        verify(authService, never()).handleOAuthCallback(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void oauthErrorWithInvalidStateFallsBackToPrimary() {
        when(authService.resolveOAuthFrontendClient("google", "invalid-state")).thenReturn(null);

        var response = controller.oauthCallback(
                "google", null, "invalid-state", "temporarily_unavailable", request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.kro.kr/auth/callback?error=social_login_failed");
    }

    @Test
    void oauthCallbackWithoutCodeOrProviderErrorRedirectsAsFailure() {
        var response = controller.oauthCallback("google", null, null, null, request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.kro.kr/auth/callback?error=social_login_failed");
    }
}
