package com.careertuner.auth.dto;

/** 서명이 검증된 OAuth state에서 복원한 callback 반환 문맥. */
public record OAuthCallbackContext(String frontendClient, boolean socialLink) {

    public static OAuthCallbackContext invalid() {
        return new OAuthCallbackContext(null, false);
    }
}
