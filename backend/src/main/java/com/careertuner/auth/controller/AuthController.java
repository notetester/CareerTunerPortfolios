package com.careertuner.auth.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.dto.LoginRequestContext;
import com.careertuner.auth.dto.LoginResponse;
import com.careertuner.auth.dto.MeResponse;
import com.careertuner.auth.dto.FindIdRequest;
import com.careertuner.auth.dto.FindIdVerifyResponse;
import com.careertuner.auth.dto.MfaApprovalRequest;
import com.careertuner.auth.dto.MfaBackupCodesResponse;
import com.careertuner.auth.dto.MfaChallengeResponse;
import com.careertuner.auth.dto.MfaDisableRequest;
import com.careertuner.auth.dto.MfaLoginStatusResponse;
import com.careertuner.auth.dto.MfaLoginVerifyRequest;
import com.careertuner.auth.dto.MfaSetupStartResponse;
import com.careertuner.auth.dto.MfaSetupVerifyRequest;
import com.careertuner.auth.dto.MfaStatusResponse;
import com.careertuner.auth.dto.NativeOAuthExchangeRequest;
import com.careertuner.auth.dto.NativeOAuthStartRequest;
import com.careertuner.auth.dto.NativeOAuthStartResponse;
import com.careertuner.auth.dto.OAuthCallbackResult;
import com.careertuner.auth.dto.OAuthCallbackContext;
import com.careertuner.auth.dto.OAuthProviderAvailabilityResponse;
import com.careertuner.auth.dto.PasswordResetConfirmRequest;
import com.careertuner.auth.dto.PasswordResetRequest;
import com.careertuner.auth.dto.RefreshRequest;
import com.careertuner.auth.dto.RegisterRequest;
import com.careertuner.auth.dto.TokenRequest;
import com.careertuner.auth.dto.TokenResponse;
import com.careertuner.auth.service.AuthService;
import com.careertuner.auth.service.MfaService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.common.web.FrontendReturnTarget;
import com.careertuner.common.web.FrontendReturnUrlResolver;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인증 API. 프런트엔드 프록시 기준 모든 경로는 /api/auth/** 하위.
 *
 * <p>소셜 로그인: 프런트가 /api/auth/oauth/{provider} 로 전체 페이지 이동 → 제공자 인증 →
 * /api/auth/oauth/{provider}/callback 에서 처리 후 웹은 /auth/browser-callback으로,
 * 네이티브 handoff는 verified HTTPS /auth/callback으로 리다이렉트.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String NATIVE_OAUTH_CALLBACK = "https://careertuner.example.com/auth/callback";
    private static final String NATIVE_PROFILE_DETAIL = "https://careertuner.example.com/profile/detail";
    private static final String BROWSER_OAUTH_CALLBACK_PATH = "/auth/browser-callback";
    private static final String BROWSER_SOCIAL_LINK_CALLBACK_PATH = "/profile/social-callback";

    private final AuthService authService;
    private final MfaService mfaService;
    private final FrontendReturnUrlResolver frontendReturnUrlResolver;

    // ── 이메일 회원가입/로그인 ──

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                               HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.register(
                request, LoginRequestContext.from(servletRequest), frontendReturnUrlResolver.resolve(servletRequest)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(request, LoginRequestContext.from(servletRequest)));
    }

    @PostMapping("/mfa/login/verify")
    public ApiResponse<LoginResponse> verifyMfaLogin(@Valid @RequestBody MfaLoginVerifyRequest request,
                                                     HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.verifyMfaLogin(request, LoginRequestContext.from(servletRequest)));
    }

    @GetMapping("/mfa/login/status")
    public ApiResponse<MfaLoginStatusResponse> mfaLoginStatus(@RequestParam String challengeToken,
                                                             HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.mfaLoginStatus(challengeToken, LoginRequestContext.from(servletRequest)));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                              HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.refresh(request.refreshToken(), LoginRequestContext.from(servletRequest)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest request,
                                    HttpServletRequest servletRequest) {
        if (request != null) {
            authService.logout(request.refreshToken(), LoginRequestContext.from(servletRequest));
        }
        return ApiResponse.ok();
    }

    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(@AuthenticationPrincipal AuthUser authUser,
                                       @RequestBody(required = false) RefreshRequest request,
                                       HttpServletRequest servletRequest) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            authService.logoutAllByRefreshToken(
                    request.refreshToken(), LoginRequestContext.from(servletRequest));
        } else if (authUser != null) {
            authService.logoutAll(authUser.id());
        }
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(authService.me(authUser.id()));
    }

    @GetMapping("/mfa/status")
    public ApiResponse<MfaStatusResponse> mfaStatus(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(mfaService.status(authUser));
    }

    @PostMapping("/mfa/setup/start")
    public ApiResponse<MfaSetupStartResponse> startMfaSetup(@AuthenticationPrincipal AuthUser authUser,
                                                            @RequestParam(required = false) String deviceName) {
        return ApiResponse.ok(mfaService.startSetup(authUser, deviceName));
    }

    @PostMapping("/mfa/setup/verify")
    public ApiResponse<MfaBackupCodesResponse> verifyMfaSetup(@AuthenticationPrincipal AuthUser authUser,
                                                              @Valid @RequestBody MfaSetupVerifyRequest request) {
        return ApiResponse.ok(mfaService.verifySetup(authUser, request.code()));
    }

    @PostMapping("/mfa/disable")
    public ApiResponse<Void> disableMfa(@AuthenticationPrincipal AuthUser authUser,
                                        @RequestBody MfaDisableRequest request) {
        mfaService.disable(authUser, request);
        return ApiResponse.ok();
    }

    @PostMapping("/mfa/backup-codes/regenerate")
    public ApiResponse<MfaBackupCodesResponse> regenerateMfaBackupCodes(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(mfaService.regenerateBackupCodes(authUser));
    }

    @GetMapping("/mfa/push/pending")
    public ApiResponse<java.util.List<MfaChallengeResponse>> pendingMfaPush(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(mfaService.pendingPushChallenges(authUser));
    }

    @PostMapping("/mfa/push/approve")
    public ApiResponse<Void> approveMfaPush(@AuthenticationPrincipal AuthUser authUser,
                                            @Valid @RequestBody MfaApprovalRequest request) {
        mfaService.approvePushChallenge(authUser, request);
        return ApiResponse.ok();
    }

    // ── 중복 체크 ──

    @GetMapping("/check/email")
    public ApiResponse<Map<String, Boolean>> checkEmail(@RequestParam String value) {
        return ApiResponse.ok(Map.of("duplicate", authService.isEmailTaken(value)));
    }

    @GetMapping("/check/login-id")
    public ApiResponse<Map<String, Boolean>> checkLoginId(@RequestParam String value) {
        return ApiResponse.ok(Map.of("duplicate", authService.isLoginIdTaken(value)));
    }

    // ── 이메일 인증 ──

    /** 이메일 링크 클릭 진입점. 검증 후 프런트 결과 페이지로 리다이렉트. */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        var result = authService.verifyEmailResult(token);
        FrontendReturnTarget target = frontendReturnUrlResolver.resolveStoredClient(result.frontendClient());
        return redirect(target.absoluteUrl("/auth/verify-email/result?success=" + result.success()));
    }

    @PostMapping("/email/resend")
    public ApiResponse<Void> resendVerification(@RequestParam String email, HttpServletRequest servletRequest) {
        authService.resendVerification(email, frontendReturnUrlResolver.resolve(servletRequest));
        return ApiResponse.ok();
    }

    @PostMapping("/find-id/request")
    public ApiResponse<Void> requestFindId(@Valid @RequestBody FindIdRequest request,
                                           HttpServletRequest servletRequest) {
        authService.requestFindId(request.email(), LoginRequestContext.from(servletRequest),
                frontendReturnUrlResolver.resolve(servletRequest));
        return ApiResponse.ok();
    }

    @GetMapping("/find-id/verify")
    public ApiResponse<FindIdVerifyResponse> verifyFindId(@RequestParam String token,
                                                          HttpServletRequest servletRequest) {
        return ApiResponse.ok(new FindIdVerifyResponse(
                authService.verifyFindId(token, LoginRequestContext.from(servletRequest))));
    }

    @PostMapping("/password/reset-request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                  HttpServletRequest servletRequest) {
        authService.requestPasswordReset(request, LoginRequestContext.from(servletRequest),
                frontendReturnUrlResolver.resolve(servletRequest));
        return ApiResponse.ok();
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request,
                                           HttpServletRequest servletRequest) {
        authService.resetPassword(request, LoginRequestContext.from(servletRequest));
        return ApiResponse.ok();
    }

    @PostMapping("/dormant/release-request")
    public ApiResponse<Void> requestDormantRelease(@Valid @RequestBody PasswordResetRequest request,
                                                   HttpServletRequest servletRequest) {
        authService.requestDormantRelease(request, LoginRequestContext.from(servletRequest),
                frontendReturnUrlResolver.resolve(servletRequest));
        return ApiResponse.ok();
    }

    @PostMapping("/dormant/release")
    public ApiResponse<TokenResponse> releaseDormant(@Valid @RequestBody TokenRequest request,
                                                     HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.releaseDormant(request, LoginRequestContext.from(servletRequest)));
    }

    // ── 소셜 로그인 ──

    @GetMapping("/oauth/providers")
    public ApiResponse<OAuthProviderAvailabilityResponse> oauthProviders() {
        return ApiResponse.ok(authService.oauthProviders());
    }

    @GetMapping("/oauth/{provider}")
    public ResponseEntity<Void> oauthRedirect(@PathVariable String provider, HttpServletRequest servletRequest) {
        return redirect(authService.buildAuthorizationUrl(provider, frontendReturnUrlResolver.resolve(servletRequest)));
    }

    @PostMapping("/oauth/{provider}/native/start")
    public ApiResponse<NativeOAuthStartResponse> nativeOAuthStart(
            @PathVariable String provider,
            @Valid @RequestBody NativeOAuthStartRequest request) {
        return ApiResponse.ok(new NativeOAuthStartResponse(
                authService.buildNativeAuthorizationUrl(provider, request.handoffChallenge())));
    }

    @PostMapping("/oauth/native/exchange")
    public ApiResponse<TokenResponse> exchangeNativeOAuth(
            @Valid @RequestBody NativeOAuthExchangeRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.exchangeNativeOAuthHandoff(
                request.handoffCode(), request.handoffVerifier(), LoginRequestContext.from(servletRequest)));
    }

    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> oauthCallback(@PathVariable String provider,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(required = false) String state,
                                              @RequestParam(required = false) String error,
                                              HttpServletRequest servletRequest) {
        OAuthCallbackContext failureContext = authService.resolveOAuthCallbackContext(provider, state);
        if (error != null && !error.isBlank()) {
            return redirectOAuthFailure(failureContext, oauthFailureCode(error));
        }
        if (code == null || code.isBlank()) {
            return redirectOAuthFailure(failureContext, "social_login_failed");
        }
        try {
            OAuthCallbackResult result = authService.handleOAuthCallback(provider, code, state,
                    LoginRequestContext.from(servletRequest));
            return redirectOAuthResult(result, false);
        } catch (Exception e) {
            log.warn("[{}] OAuth 콜백 실패: {}", provider, e.getMessage());
            return redirectOAuthFailure(failureContext, "social_login_failed");
        }
    }

    @GetMapping("/oauth/{provider}/mock-callback")
    public ResponseEntity<Void> oauthMockCallback(@PathVariable String provider,
                                                  @RequestParam(required = false) String state,
                                                  HttpServletRequest servletRequest) {
        try {
            OAuthCallbackResult result = authService.handleOAuthMockCallback(provider, state,
                    LoginRequestContext.from(servletRequest));
            return redirectOAuthResult(result, true);
        } catch (Exception e) {
            log.warn("[{}] OAuth mock 콜백 실패: {}", provider, e.getMessage());
            return redirectOAuthFailure(
                    authService.resolveOAuthCallbackContext(provider, state), "social_login_failed");
        }
    }

    // ── 내부 ──

    private ResponseEntity<Void> redirectOAuthResult(OAuthCallbackResult result, boolean mock) {
        if (result.nativeHandoff()) {
            return redirect(NATIVE_OAUTH_CALLBACK + "?handoffCode=" + enc(result.handoffCode()));
        }
        if (result.linked()) {
            String query = "?socialLinked=" + enc(result.provider());
            if (mock) {
                query += "&socialMock=1";
            }
            if (FrontendReturnUrlResolver.NATIVE_CLIENT.equals(result.frontendClient())) {
                return redirect(NATIVE_PROFILE_DETAIL + query);
            }
            FrontendReturnTarget target = frontendReturnUrlResolver.resolveStoredClient(result.frontendClient());
            // 브라우저 반환 경로를 verified App Link와 분리한다. 앱이 설치된 모바일 브라우저에서도
            // 웹 계정 연결 결과가 다른 네이티브 세션으로 가로채지지 않아야 한다.
            return redirect(target.absoluteUrl(BROWSER_SOCIAL_LINK_CALLBACK_PATH + query));
        }
        FrontendReturnTarget target = frontendReturnUrlResolver.resolveStoredClient(result.frontendClient());
        TokenResponse tokens = result.tokens();
        String fragment = BROWSER_OAUTH_CALLBACK_PATH + "#accessToken=" + enc(tokens.accessToken())
                + "&refreshToken=" + enc(tokens.refreshToken())
                + "&expiresIn=" + tokens.expiresIn();
        if (mock) {
            fragment += "&mockOAuth=1";
        }
        return redirect(target.absoluteUrl(fragment));
    }

    private ResponseEntity<Void> redirectOAuthFailure(OAuthCallbackContext context, String errorCode) {
        String frontendClient = context != null ? context.frontendClient() : null;
        if (FrontendReturnUrlResolver.NATIVE_CLIENT.equals(frontendClient)) {
            if (context.socialLink()) {
                return redirect(NATIVE_PROFILE_DETAIL + "?socialLinkError=" + enc(errorCode));
            }
            return redirect(NATIVE_OAUTH_CALLBACK + "?error=" + enc(errorCode));
        }
        FrontendReturnTarget target = frontendReturnUrlResolver.resolveStoredClient(frontendClient);
        if (context != null && context.socialLink()) {
            return redirect(target.absoluteUrl(
                    BROWSER_SOCIAL_LINK_CALLBACK_PATH + "?socialLinkError=" + enc(errorCode)));
        }
        return redirect(target.absoluteUrl(BROWSER_OAUTH_CALLBACK_PATH + "?error=" + enc(errorCode)));
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static String oauthFailureCode(String providerError) {
        return switch (providerError) {
            case "access_denied", "user_cancelled", "consent_declined" -> "social_login_cancelled";
            default -> "social_login_failed";
        };
    }
}
