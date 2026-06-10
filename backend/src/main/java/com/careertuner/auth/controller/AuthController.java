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
import com.careertuner.auth.dto.MeResponse;
import com.careertuner.auth.dto.RefreshRequest;
import com.careertuner.auth.dto.RegisterRequest;
import com.careertuner.auth.dto.TokenResponse;
import com.careertuner.auth.service.AuthService;
import com.careertuner.common.config.CareerTunerProperties;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

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
    private final CareerTunerProperties props;

    // ── 이메일 회원가입/로그인 ──

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                               HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.register(request, LoginRequestContext.from(servletRequest)));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(request, LoginRequestContext.from(servletRequest)));
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

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(authService.me(authUser.id()));
    }

    // ── 중복 체크 ──

    @GetMapping("/check/email")
    public ApiResponse<Map<String, Boolean>> checkEmail(@RequestParam String value) {
        return ApiResponse.ok(Map.of("duplicate", authService.isEmailTaken(value)));
    }

    // ── 이메일 인증 ──

    /** 이메일 링크 클릭 진입점. 검증 후 프런트 결과 페이지로 리다이렉트. */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        boolean ok = authService.verifyEmail(token);
        return redirect(props.getApp().getFrontendUrl() + "/auth/verify-email/result?success=" + ok);
    }

    @PostMapping("/email/resend")
    public ApiResponse<Void> resendVerification(@RequestParam String email) {
        authService.resendVerification(email);
        return ApiResponse.ok();
    }

    // ── 소셜 로그인 ──

    @GetMapping("/oauth/{provider}")
    public ResponseEntity<Void> oauthRedirect(@PathVariable String provider) {
        return redirect(authService.buildAuthorizationUrl(provider));
    }

    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> oauthCallback(@PathVariable String provider,
                                              @RequestParam String code,
                                              @RequestParam(required = false) String state,
                                              HttpServletRequest servletRequest) {
        String frontend = props.getApp().getFrontendUrl();
        try {
            TokenResponse tokens = authService.handleOAuthCallback(provider, code, state,
                    LoginRequestContext.from(servletRequest));
            String fragment = "/auth/callback#accessToken=" + enc(tokens.accessToken())
                    + "&refreshToken=" + enc(tokens.refreshToken())
                    + "&expiresIn=" + tokens.expiresIn();
            return redirect(frontend + fragment);
        } catch (Exception e) {
            log.warn("[{}] OAuth 콜백 실패: {}", provider, e.getMessage());
            return redirect(frontend + "/auth/callback#error=" + enc("social_login_failed"));
        }
    }

    // ── 내부 ──

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
