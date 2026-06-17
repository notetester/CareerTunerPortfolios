package com.careertuner.auth.service;

import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.dto.LoginRequestContext;
import com.careertuner.auth.dto.MeResponse;
import com.careertuner.auth.dto.PasswordResetConfirmRequest;
import com.careertuner.auth.dto.PasswordResetRequest;
import com.careertuner.auth.dto.RegisterRequest;
import com.careertuner.auth.dto.TokenRequest;
import com.careertuner.auth.dto.TokenResponse;

public interface AuthService {

    TokenResponse register(RegisterRequest request, LoginRequestContext context);

    TokenResponse login(LoginRequest request, LoginRequestContext context);

    TokenResponse refresh(String refreshToken, LoginRequestContext context);

    void logout(String refreshToken, LoginRequestContext context);

    /** 모든 기기 로그아웃 — 해당 사용자의 모든 refresh 토큰을 폐기한다. */
    void logoutAll(Long userId);

    boolean verifyEmail(String token);

    void resendVerification(String email);

    void requestPasswordReset(PasswordResetRequest request, LoginRequestContext context);

    void resetPassword(PasswordResetConfirmRequest request, LoginRequestContext context);

    void requestDormantRelease(PasswordResetRequest request, LoginRequestContext context);

    TokenResponse releaseDormant(TokenRequest request, LoginRequestContext context);

    boolean isEmailTaken(String email);

    MeResponse me(Long userId);

    /** 소셜 제공자 인가 URL(서명된 state 포함)을 만든다. */
    String buildAuthorizationUrl(String provider);

    /** 소셜 콜백 처리: state 검증 → 사용자 조회/생성 → 토큰 발급. */
    TokenResponse handleOAuthCallback(String provider, String code, String state, LoginRequestContext context);
}
