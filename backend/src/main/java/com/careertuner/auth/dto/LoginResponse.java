package com.careertuner.auth.dto;

public record LoginResponse(
        boolean mfaRequired,
        boolean mfaSetupRecommended,
        String challengeToken,
        String challengeMethod,
        long expiresIn,
        TokenResponse token
) {
    public static LoginResponse authenticated(TokenResponse token) {
        return new LoginResponse(false, false, null, null, 0, token);
    }

    public static LoginResponse mfaRequired(String challengeToken, String challengeMethod, long expiresIn) {
        return new LoginResponse(true, false, challengeToken, challengeMethod, expiresIn, null);
    }
}
