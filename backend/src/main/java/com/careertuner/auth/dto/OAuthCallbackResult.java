package com.careertuner.auth.dto;

public record OAuthCallbackResult(
        boolean linked,
        String provider,
        TokenResponse tokens,
        String frontendClient) {

    public static OAuthCallbackResult login(TokenResponse tokens) {
        return login(tokens, null);
    }

    public static OAuthCallbackResult login(TokenResponse tokens, String frontendClient) {
        return new OAuthCallbackResult(false, null, tokens, frontendClient);
    }

    public static OAuthCallbackResult linked(String provider) {
        return linked(provider, null);
    }

    public static OAuthCallbackResult linked(String provider, String frontendClient) {
        return new OAuthCallbackResult(true, provider, null, frontendClient);
    }
}
