package com.careertuner.auth.dto;

import com.careertuner.user.domain.User;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        MeResponse user) {

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, User user) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, MeResponse.from(user));
    }
}
