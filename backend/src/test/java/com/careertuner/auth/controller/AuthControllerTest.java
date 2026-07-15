package com.careertuner.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.auth.dto.OAuthProviderAvailabilityResponse;
import com.careertuner.auth.dto.OAuthCallbackContext;
import com.careertuner.auth.dto.NativeOAuthExchangeRequest;
import com.careertuner.auth.dto.NativeOAuthStartRequest;
import com.careertuner.auth.dto.OAuthCallbackResult;
import com.careertuner.auth.dto.RefreshRequest;
import com.careertuner.auth.dto.TokenResponse;
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
            new FrontendReturnTarget("primary", "https://careertuner.example.com");
    private final FrontendReturnTarget sites = new FrontendReturnTarget(
            "sites", "https://sites.example.com");

    @BeforeEach
    void setUp() {
        when(resolver.resolveStoredClient(null)).thenReturn(primary);
        when(resolver.resolveStoredClient("sites")).thenReturn(sites);
    }

    @Test
    void oauthCancellationUsesFrontendClientFromValidStateWithoutRequiringCode() {
        when(authService.resolveOAuthCallbackContext("google", "signed-state"))
                .thenReturn(new OAuthCallbackContext("sites", false));

        var response = controller.oauthCallback(
                "google", null, "signed-state", "access_denied", request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://sites.example.com/auth/browser-callback"
                        + "?error=social_login_cancelled");
        verify(authService, never()).handleOAuthCallback(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void oauthErrorWithInvalidStateFallsBackToPrimary() {
        when(authService.resolveOAuthCallbackContext("google", "invalid-state"))
                .thenReturn(OAuthCallbackContext.invalid());

        var response = controller.oauthCallback(
                "google", null, "invalid-state", "temporarily_unavailable", request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.example.com/auth/browser-callback?error=social_login_failed");
    }

    @Test
    void oauthCallbackWithoutCodeOrProviderErrorRedirectsAsFailure() {
        var response = controller.oauthCallback("google", null, null, null, request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.example.com/auth/browser-callback?error=social_login_failed");
    }

    @Test
    void oauthProviders_returnsCurrentServiceAvailability() {
        var availability = new OAuthProviderAvailabilityResponse(true, false, true);
        when(authService.oauthProviders()).thenReturn(availability);

        var response = controller.oauthProviders();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(availability);
    }

    @Test
    void nativeOAuthStartReturnsOnlyAuthorizationUrlEnvelope() {
        String challenge = "A".repeat(43);
        when(authService.buildNativeAuthorizationUrl("kakao", challenge))
                .thenReturn("https://kauth.kakao.com/oauth/authorize?state=signed");

        var response = controller.nativeOAuthStart("kakao", new NativeOAuthStartRequest(challenge));

        assertThat(response.success()).isTrue();
        assertThat(response.data().authorizationUrl()).startsWith("https://kauth.kakao.com/");
    }

    @Test
    void nativeOAuthCallbackRedirectContainsOnlyOneTimeHandoffCode() {
        String handoffCode = "H".repeat(43);
        when(authService.handleOAuthCallback(
                org.mockito.ArgumentMatchers.eq("kakao"), org.mockito.ArgumentMatchers.eq("provider-code"),
                org.mockito.ArgumentMatchers.eq("native-state"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(OAuthCallbackResult.nativeLogin(handoffCode));

        var response = controller.oauthCallback(
                "kakao", "provider-code", "native-state", null, request);

        String location = response.getHeaders().getLocation().toString();
        assertThat(location).isEqualTo("https://careertuner.example.com/auth/callback?handoffCode=" + handoffCode);
        assertThat(location).doesNotContain("accessToken", "refreshToken", "#");
    }

    @Test
    void nativeMockCallbackAlsoRedirectsWithOnlyHandoffCode() {
        String handoffCode = "M".repeat(43);
        when(authService.handleOAuthMockCallback(
                org.mockito.ArgumentMatchers.eq("naver"), org.mockito.ArgumentMatchers.eq("native-state"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(OAuthCallbackResult.nativeLogin(handoffCode));

        var response = controller.oauthMockCallback("naver", "native-state", request);

        String location = response.getHeaders().getLocation().toString();
        assertThat(location).isEqualTo("https://careertuner.example.com/auth/callback?handoffCode=" + handoffCode);
        assertThat(location).doesNotContain("accessToken", "refreshToken", "mockOAuth");
    }

    @Test
    void nativeOAuthCancellationReturnsToVerifiedAppLink() {
        when(authService.resolveOAuthCallbackContext("naver", "native-state"))
                .thenReturn(new OAuthCallbackContext("native", false));

        var response = controller.oauthCallback(
                "naver", null, "native-state", "access_denied", request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.example.com/auth/callback?error=social_login_cancelled");
    }

    @Test
    void nativeOAuthExchangeReturnsExistingTokenResponseShape() {
        String handoffCode = "C".repeat(43);
        String verifier = "V".repeat(43);
        TokenResponse tokens = new TokenResponse("access", "refresh", "Bearer", 1800, null);
        when(authService.exchangeNativeOAuthHandoff(
                org.mockito.ArgumentMatchers.eq(handoffCode), org.mockito.ArgumentMatchers.eq(verifier),
                org.mockito.ArgumentMatchers.any())).thenReturn(tokens);

        var response = controller.exchangeNativeOAuth(
                new NativeOAuthExchangeRequest(handoffCode, verifier), request);

        assertThat(response.data()).isSameAs(tokens);
    }

    @Test
    void existingBrowserOAuthCallbackStillUsesTokenFragment() {
        TokenResponse tokens = new TokenResponse("browser-access", "browser-refresh", "Bearer", 1800, null);
        when(authService.handleOAuthCallback(
                org.mockito.ArgumentMatchers.eq("google"), org.mockito.ArgumentMatchers.eq("provider-code"),
                org.mockito.ArgumentMatchers.eq("browser-state"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(OAuthCallbackResult.login(tokens, "primary"));
        when(resolver.resolveStoredClient("primary")).thenReturn(primary);

        var response = controller.oauthCallback(
                "google", "provider-code", "browser-state", null, request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.example.com/auth/browser-callback#accessToken=browser-access"
                        + "&refreshToken=browser-refresh&expiresIn=1800");
    }

    @Test
    void nativeSocialLinkSuccessReturnsToVerifiedProfileAppLink() {
        when(authService.handleOAuthCallback(
                org.mockito.ArgumentMatchers.eq("google"), org.mockito.ArgumentMatchers.eq("provider-code"),
                org.mockito.ArgumentMatchers.eq("native-link-state"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(OAuthCallbackResult.linked("GOOGLE", "native"));

        var response = controller.oauthCallback(
                "google", "provider-code", "native-link-state", null, request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.example.com/profile/detail?socialLinked=GOOGLE");
    }

    @Test
    void nativeSocialLinkCancellationUsesSignedLinkContextAndProfileErrorQuery() {
        when(authService.resolveOAuthCallbackContext("kakao", "native-link-state"))
                .thenReturn(new OAuthCallbackContext("native", true));

        var response = controller.oauthCallback(
                "kakao", null, "native-link-state", "access_denied", request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.example.com/profile/detail?socialLinkError=social_login_cancelled");
    }

    @Test
    void nativeSocialLinkProcessingFailureReturnsToProfileWithoutLoginCallback() {
        when(authService.resolveOAuthCallbackContext("naver", "native-link-state"))
                .thenReturn(new OAuthCallbackContext("native", true));
        when(authService.handleOAuthCallback(
                org.mockito.ArgumentMatchers.eq("naver"), org.mockito.ArgumentMatchers.eq("provider-code"),
                org.mockito.ArgumentMatchers.eq("native-link-state"), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("provider failed"));

        var response = controller.oauthCallback(
                "naver", "provider-code", "native-link-state", null, request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.example.com/profile/detail?socialLinkError=social_login_failed");
    }

    @Test
    void sitesSocialLinkSuccessUsesBrowserOnlyCallbackPath() {
        when(authService.handleOAuthCallback(
                org.mockito.ArgumentMatchers.eq("kakao"), org.mockito.ArgumentMatchers.eq("provider-code"),
                org.mockito.ArgumentMatchers.eq("sites-link-state"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(OAuthCallbackResult.linked("KAKAO", "sites"));

        var response = controller.oauthCallback(
                "kakao", "provider-code", "sites-link-state", null, request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://sites.example.com/profile/social-callback?socialLinked=KAKAO");
    }

    @Test
    void browserSocialLinkSuccessAvoidsVerifiedAppLinkPath() {
        when(authService.handleOAuthCallback(
                org.mockito.ArgumentMatchers.eq("google"), org.mockito.ArgumentMatchers.eq("provider-code"),
                org.mockito.ArgumentMatchers.eq("browser-link-state"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(OAuthCallbackResult.linked("GOOGLE", "primary"));
        when(resolver.resolveStoredClient("primary")).thenReturn(primary);

        var response = controller.oauthCallback(
                "google", "provider-code", "browser-link-state", null, request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.example.com/profile/social-callback?socialLinked=GOOGLE");
    }

    @Test
    void browserSocialLinkFailureReturnsToBrowserOnlyProfileCallback() {
        when(authService.resolveOAuthCallbackContext("google", "browser-link-state"))
                .thenReturn(new OAuthCallbackContext("primary", true));
        when(resolver.resolveStoredClient("primary")).thenReturn(primary);

        var response = controller.oauthCallback(
                "google", null, "browser-link-state", "access_denied", request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://careertuner.example.com/profile/social-callback?socialLinkError=social_login_cancelled");
    }

    @Test
    void sitesSocialLinkFailureReturnsToSitesProfileCallback() {
        when(authService.resolveOAuthCallbackContext("naver", "sites-link-state"))
                .thenReturn(new OAuthCallbackContext("sites", true));

        var response = controller.oauthCallback(
                "naver", null, "sites-link-state", "temporarily_unavailable", request);

        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://sites.example.com/profile/social-callback"
                        + "?socialLinkError=social_login_failed");
    }

    @Test
    void logoutUsesRefreshTokenWithoutRequiringAnAccessPrincipal() {
        controller.logout(new RefreshRequest("refresh-one"), request);

        verify(authService).logout(
                org.mockito.ArgumentMatchers.eq("refresh-one"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void logoutAllUsesRefreshTokenWhenAccessTokenIsExpired() {
        controller.logoutAll(null, new RefreshRequest("refresh-all"), request);

        verify(authService).logoutAllByRefreshToken(
                org.mockito.ArgumentMatchers.eq("refresh-all"),
                org.mockito.ArgumentMatchers.any());
        verify(authService, never()).logoutAll(org.mockito.ArgumentMatchers.anyLong());
    }
}
