package com.careertuner.auth.service;

/** 소셜 제공자에서 받아온 사용자 식별 정보. */
public record SocialUserInfo(String provider, String providerUserId, String email, String name) {
}
