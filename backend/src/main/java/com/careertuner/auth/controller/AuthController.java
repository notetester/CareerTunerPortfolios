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
import com.careertuner.auth.dto.OAuthCallbackResult;
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
 * /api/auth/oauth/{provider}/callback 에서 처리 후 프런트 /auth/callback 으로 토큰과 함께 리다이렉트.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

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
    public ApiResponse<Void> logoutAll(@AuthenticationPrincipal AuthUser authUser) {
        authService.logoutAll(authUser.id());
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

    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> oauthCallback(@PathVariable String provider,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(required = false) String state,
                                              @RequestParam(required = false) String error,
                                              HttpServletRequest servletRequest) {
        FrontendReturnTarget failureTarget = frontendReturnUrlResolver.resolveStoredClient(
                authService.resolveOAuthFrontendClient(provider, state));
        if (error != null && !error.isBlank()) {
            return redirect(failureTarget.absoluteUrl("/auth/callback?error=" + enc(oauthFailureCode(error))));
        }
        if (code == null || code.isBlank()) {
            return redirect(failureTarget.absoluteUrl("/auth/callback?error=" + enc("social_login_failed")));
        }
        try {
            OAuthCallbackResult result = authService.handleOAuthCallback(provider, code, state,
                    LoginRequestContext.from(servletRequest));
            return redirectOAuthResult(frontendReturnUrlResolver.resolveStoredClient(result.frontendClient()),
                    result, false);
        } catch (Exception e) {
            log.warn("[{}] OAuth 콜백 실패: {}", provider, e.getMessage());
            return redirect(failureTarget.absoluteUrl("/auth/callback?error=" + enc("social_login_failed")));
        }
    }

    @GetMapping("/oauth/{provider}/mock-callback")
    public ResponseEntity<Void> oauthMockCallback(@PathVariable String provider,
                                                  @RequestParam(required = false) String state,
                                                  HttpServletRequest servletRequest) {
        try {
            OAuthCallbackResult result = authService.handleOAuthMockCallback(provider, state,
                    LoginRequestContext.from(servletRequest));
            return redirectOAuthResult(frontendReturnUrlResolver.resolveStoredClient(result.frontendClient()),
                    result, true);
        } catch (Exception e) {
            log.warn("[{}] OAuth mock 콜백 실패: {}", provider, e.getMessage());
            FrontendReturnTarget target = frontendReturnUrlResolver.resolveStoredClient(
                    authService.resolveOAuthFrontendClient(provider, state));
            return redirect(target.absoluteUrl("/auth/callback?error=" + enc("social_login_failed")));
        }
    }

    // ── 내부 ──

    private ResponseEntity<Void> redirectOAuthResult(
            FrontendReturnTarget target, OAuthCallbackResult result, boolean mock) {
        if (result.linked()) {
            String query = "/profile/detail?socialLinked=" + enc(result.provider());
            if (mock) {
                query += "&socialMock=1";
            }
            return redirect(target.absoluteUrl(query));
        }
        TokenResponse tokens = result.tokens();
        String fragment = "/auth/callback#accessToken=" + enc(tokens.accessToken())
                + "&refreshToken=" + enc(tokens.refreshToken())
                + "&expiresIn=" + tokens.expiresIn();
        if (mock) {
            fragment += "&mockOAuth=1";
        }
        return redirect(target.absoluteUrl(fragment));
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
