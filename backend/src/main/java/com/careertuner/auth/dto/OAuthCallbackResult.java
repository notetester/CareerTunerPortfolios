package com.careertuner.auth.dto;

public record OAuthCallbackResult(
        boolean linked,
        String provider,
        TokenResponse tokens) {

    public static OAuthCallbackResult login(TokenResponse tokens) {
        return new OAuthCallbackResult(false, null, tokens);
    }

    public static OAuthCallbackResult linked(String provider) {
        return new OAuthCallbackResult(true, provider, null);
    }
}
