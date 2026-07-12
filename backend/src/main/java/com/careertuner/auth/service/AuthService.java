package com.careertuner.auth.service;

import com.careertuner.auth.dto.EmailVerificationResult;
import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.dto.LoginRequestContext;
import com.careertuner.auth.dto.LoginResponse;
import com.careertuner.auth.dto.MeResponse;
import com.careertuner.auth.dto.MfaLoginStatusResponse;
import com.careertuner.auth.dto.MfaLoginVerifyRequest;
import com.careertuner.auth.dto.OAuthCallbackResult;
import com.careertuner.auth.dto.OAuthCallbackContext;
import com.careertuner.auth.dto.OAuthProviderAvailabilityResponse;
import com.careertuner.auth.dto.PasswordResetConfirmRequest;
import com.careertuner.auth.dto.PasswordResetRequest;
import com.careertuner.auth.dto.RegisterRequest;
import com.careertuner.auth.dto.TokenRequest;
import com.careertuner.auth.dto.TokenResponse;
import com.careertuner.common.web.FrontendReturnTarget;

public interface AuthService {

    TokenResponse register(RegisterRequest request, LoginRequestContext context);

    TokenResponse register(RegisterRequest request, LoginRequestContext context, FrontendReturnTarget returnTarget);

    LoginResponse login(LoginRequest request, LoginRequestContext context);

    LoginResponse verifyMfaLogin(MfaLoginVerifyRequest request, LoginRequestContext context);

    MfaLoginStatusResponse mfaLoginStatus(String challengeToken, LoginRequestContext context);

    TokenResponse refresh(String refreshToken, LoginRequestContext context);

    void logout(String refreshToken, LoginRequestContext context);

    /** 모든 기기 로그아웃 — 해당 사용자의 모든 refresh 토큰을 폐기한다. */
    void logoutAll(Long userId);

    /** access token 만료 상태에서도 현재 refresh token 소유자의 모든 세션을 폐기한다. */
    void logoutAllByRefreshToken(String refreshToken, LoginRequestContext context);

    boolean verifyEmail(String token);

    EmailVerificationResult verifyEmailResult(String token);

    void resendVerification(String email);

    void resendVerification(String email, FrontendReturnTarget returnTarget);

    void requestFindId(String email, LoginRequestContext context);

    void requestFindId(String email, LoginRequestContext context, FrontendReturnTarget returnTarget);

    String verifyFindId(String token, LoginRequestContext context);

    void requestPasswordReset(PasswordResetRequest request, LoginRequestContext context);

    void requestPasswordReset(PasswordResetRequest request, LoginRequestContext context,
                              FrontendReturnTarget returnTarget);

    void resetPassword(PasswordResetConfirmRequest request, LoginRequestContext context);

    void requestDormantRelease(PasswordResetRequest request, LoginRequestContext context);

    void requestDormantRelease(PasswordResetRequest request, LoginRequestContext context,
                               FrontendReturnTarget returnTarget);

    TokenResponse releaseDormant(TokenRequest request, LoginRequestContext context);

    boolean isEmailTaken(String email);

    boolean isLoginIdTaken(String loginId);

    MeResponse me(Long userId);

    /** 현재 환경에서 시작 가능한 소셜 OAuth 제공자를 반환한다. */
    OAuthProviderAvailabilityResponse oauthProviders();

    /** 소셜 제공자 인가 URL(서명된 state 포함)을 만든다. */
    String buildAuthorizationUrl(String provider);

    String buildAuthorizationUrl(String provider, FrontendReturnTarget returnTarget);

    /** 구글/카카오/네이버 네이티브 OAuth를 PKCE-bound handoff로 시작한다. */
    String buildNativeAuthorizationUrl(String provider, String handoffChallenge);

    /** 로그인한 사용자가 소셜 계정을 연결하기 위한 인가 URL을 만든다. */
    String buildSocialLinkUrl(Long userId, String provider);

    String buildSocialLinkUrl(Long userId, String provider, FrontendReturnTarget returnTarget);

    /** 콜백 실패 시에도 서명이 유효한 state의 named client만 복원한다. */
    String resolveOAuthFrontendClient(String provider, String state);

    /** 콜백 실패 반환에 필요한 client/link 여부를 서명이 유효한 state에서만 복원한다. */
    OAuthCallbackContext resolveOAuthCallbackContext(String provider, String state);

    /** 소셜 콜백 처리: state 검증 → 사용자 조회/생성 → 토큰 발급. */
    OAuthCallbackResult handleOAuthCallback(String provider, String code, String state, LoginRequestContext context);

    OAuthCallbackResult handleOAuthMockCallback(String provider, String state, LoginRequestContext context);

    /** 일회성 handoff를 잠그고 소비한 뒤 CareerTuner 토큰을 발급한다. */
    TokenResponse exchangeNativeOAuthHandoff(
            String handoffCode, String handoffVerifier, LoginRequestContext context);
}
