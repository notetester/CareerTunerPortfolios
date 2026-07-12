package com.careertuner.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class SocialOAuthServiceTest {

    @Test
    void googleTrustsEmailOnlyWhenVerifiedEmailIsBooleanTrue() {
        SocialUserInfo verified = SocialOAuthService.googleUserInfo(Map.of(
                "id", "google-1", "email", "verified@example.com", "name", "사용자",
                "verified_email", true));
        SocialUserInfo unverified = SocialOAuthService.googleUserInfo(Map.of(
                "id", "google-2", "email", "unverified@example.com", "name", "사용자",
                "verified_email", false));
        SocialUserInfo missingFlag = SocialOAuthService.googleUserInfo(Map.of(
                "id", "google-3", "email", "missing@example.com", "name", "사용자"));

        assertThat(verified.emailVerified()).isTrue();
        assertThat(unverified.emailVerified()).isFalse();
        assertThat(missingFlag.emailVerified()).isFalse();
    }

    @Test
    void kakaoRequiresBothEmailValidityAndVerificationFlags() {
        SocialUserInfo verified = SocialOAuthService.kakaoUserInfo(kakaoResponse(true, true));
        SocialUserInfo invalid = SocialOAuthService.kakaoUserInfo(kakaoResponse(false, true));
        SocialUserInfo unverified = SocialOAuthService.kakaoUserInfo(kakaoResponse(true, false));
        SocialUserInfo stringFlagsAreNotTrusted = SocialOAuthService.kakaoUserInfo(Map.of(
                "id", 4L,
                "kakao_account", Map.of(
                        "email", "kakao@example.com",
                        "is_email_valid", "true",
                        "is_email_verified", "true")));

        assertThat(verified.emailVerified()).isTrue();
        assertThat(invalid.emailVerified()).isFalse();
        assertThat(unverified.emailVerified()).isFalse();
        assertThat(stringFlagsAreNotTrusted.emailVerified()).isFalse();
    }

    @Test
    void naverEmailIsNotTrustedBecauseOfficialProfileResponseHasNoVerificationFlag() {
        SocialUserInfo info = SocialOAuthService.naverUserInfo(Map.of(
                "response", Map.of(
                        "id", "naver-1",
                        "email", "naver@example.com",
                        "name", "사용자")));

        assertThat(info.email()).isEqualTo("naver@example.com");
        assertThat(info.emailVerified()).isFalse();
    }

    private static Map<String, Object> kakaoResponse(boolean valid, boolean verified) {
        return Map.of(
                "id", 1L,
                "kakao_account", Map.of(
                        "email", "kakao@example.com",
                        "is_email_valid", valid,
                        "is_email_verified", verified));
    }
}
