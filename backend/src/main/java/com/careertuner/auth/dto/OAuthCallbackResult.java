package com.careertuner.auth.dto;

public record OAuthCallbackResult(
        boolean linked,
        String provider,
        TokenResponse tokens,
        String frontendClient,
        String handoffCode) {

    public static OAuthCallbackResult login(TokenResponse tokens) {
        return login(tokens, null);
    }

    public static OAuthCallbackResult login(TokenResponse tokens, String frontendClient) {
        return new OAuthCallbackResult(false, null, tokens, frontendClient, null);
    }

    public static OAuthCallbackResult nativeLogin(String handoffCode) {
        return new OAuthCallbackResult(false, null, null, "native", handoffCode);
    }

    public static OAuthCallbackResult linked(String provider) {
        return linked(provider, null);
    }

    public static OAuthCallbackResult linked(String provider, String frontendClient) {
        return new OAuthCallbackResult(true, provider, null, frontendClient, null);
    }

    public boolean nativeHandoff() {
        return handoffCode != null;
    }
}
