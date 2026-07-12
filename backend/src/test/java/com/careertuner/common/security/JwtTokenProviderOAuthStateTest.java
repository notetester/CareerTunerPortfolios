package com.careertuner.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.common.config.CareerTunerProperties;

class JwtTokenProviderOAuthStateTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        CareerTunerProperties props = new CareerTunerProperties();
        props.getJwt().setSecret("test-only-careertuner-jwt-secret-0123456789abcdef");
        provider = new JwtTokenProvider(props);
    }

    @Test
    void signedLoginStateRoundTripsFrontendClient() {
        String state = provider.createOauthState("GOOGLE", "sites");

        JwtTokenProvider.OauthState parsed = provider.parseOauthState(state, "GOOGLE");

        assertThat(parsed).isNotNull();
        assertThat(parsed.login()).isTrue();
        assertThat(parsed.frontendClient()).isEqualTo("sites");
    }

    @Test
    void signedLinkStateKeepsUserAndFrontendClient() {
        String state = provider.createOauthLinkState("KAKAO", 42L, "primary");

        JwtTokenProvider.OauthState parsed = provider.parseOauthState(state, "KAKAO");

        assertThat(parsed).isNotNull();
        assertThat(parsed.link()).isTrue();
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.frontendClient()).isEqualTo("primary");
    }

    @Test
    void legacyStateWithoutFrontendClientRemainsValid() {
        String state = provider.createOauthState("NAVER");

        JwtTokenProvider.OauthState parsed = provider.parseOauthState(state, "NAVER");

        assertThat(parsed).isNotNull();
        assertThat(parsed.frontendClient()).isNull();
    }

    @Test
    void signedNativeStateBindsFixedClientAndPkceChallenge() {
        String challenge = "A".repeat(43);

        String state = provider.createNativeOauthState("KAKAO", challenge);
        JwtTokenProvider.OauthState parsed = provider.parseOauthState(state, "KAKAO");

        assertThat(parsed).isNotNull();
        assertThat(parsed.nativeLogin()).isTrue();
        assertThat(parsed.frontendClient()).isEqualTo("native");
        assertThat(parsed.handoffChallenge()).isEqualTo(challenge);
        assertThat(provider.parseOauthState(state, "NAVER")).isNull();
    }
}
