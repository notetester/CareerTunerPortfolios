package com.careertuner.auth.service;

import com.careertuner.auth.dto.LoginRequest;
import com.careertuner.auth.dto.MeResponse;
import com.careertuner.auth.dto.RegisterRequest;
import com.careertuner.auth.dto.TokenResponse;

public interface AuthService {

    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(String refreshToken);

    void logout(String refreshToken);

    boolean verifyEmail(String token);

    void resendVerification(String email);

    boolean isEmailTaken(String email);

    MeResponse me(Long userId);

    /** 소셜 제공자 인가 URL(서명된 state 포함)을 만든다. */
    String buildAuthorizationUrl(String provider);

    /** 소셜 콜백 처리: state 검증 → 사용자 조회/생성 → 토큰 발급. */
    TokenResponse handleOAuthCallback(String provider, String code, String state);
}
