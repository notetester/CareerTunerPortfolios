package com.careertuner.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.careertuner.common.config.CareerTunerProperties;

class OAuthMockSafetyGuardTest {

    @Test
    void defaultsToDisabled() {
        assertThat(new CareerTunerProperties().getOauth().isMockEnabled()).isFalse();
    }

    @Test
    void rejectsMockOutsideLocalProfile() {
        CareerTunerProperties properties = mockEnabled("http://localhost:8080");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("aws");

        assertThatThrownBy(() -> new OAuthMockSafetyGuard(properties, environment)
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local 프로파일");
    }

    @Test
    void rejectsPublicApiBaseEvenInLocalProfile() {
        CareerTunerProperties properties = mockEnabled("https://careertuner.example.com");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThatThrownBy(() -> new OAuthMockSafetyGuard(properties, environment)
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsLocalCombinedWithProductionProfile() {
        CareerTunerProperties properties = mockEnabled("http://localhost:8080");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "aws");

        assertThatThrownBy(() -> new OAuthMockSafetyGuard(properties, environment)
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void permitsExplicitLocalLoopbackDevelopment() {
        CareerTunerProperties properties = mockEnabled("http://127.0.0.1:8080");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThatCode(() -> new OAuthMockSafetyGuard(properties, environment)
                .afterSingletonsInstantiated()).doesNotThrowAnyException();
    }

    @Test
    void recognizesIpv6Loopback() {
        assertThat(OAuthMockSafetyGuard.isLoopbackApiBase("http://[::1]:8080")).isTrue();
    }

    @Test
    void deploymentFilesForceProductionBoundary() throws Exception {
        String base = Files.readString(Path.of("src/main/resources/application.yaml"));
        String local = Files.readString(Path.of("src/main/resources/application-local.yaml"));
        String aws = Files.readString(Path.of("src/main/resources/application-aws.yaml"));
        String compose = Files.readString(Path.of("../docker-compose.prod.yml"));

        assertThat(base).contains("mock-enabled: ${OAUTH_MOCK_ENABLED:false}");
        assertThat(local).contains("mock-enabled: ${OAUTH_MOCK_ENABLED:true}");
        assertThat(aws).contains("https://sites.example.com");
        assertThat(aws)
                .contains("OAUTH_GOOGLE_REDIRECT_URI:https://careertuner.example.com/api/auth/oauth/google/callback")
                .contains("OAUTH_KAKAO_REDIRECT_URI:https://careertuner.example.com/api/auth/oauth/kakao/callback")
                .contains("OAUTH_NAVER_REDIRECT_URI:https://careertuner.example.com/api/auth/oauth/naver/callback");
        assertThat(compose)
                .contains("SPRING_PROFILES_ACTIVE: aws")
                .contains("OAUTH_MOCK_ENABLED: \"false\"")
                .contains("APP_SITES_FRONTEND_URL: https://sites.example.com")
                .contains("OAUTH_GOOGLE_REDIRECT_URI: https://careertuner.example.com/api/auth/oauth/google/callback")
                .contains("OAUTH_KAKAO_REDIRECT_URI: https://careertuner.example.com/api/auth/oauth/kakao/callback")
                .contains("OAUTH_NAVER_REDIRECT_URI: https://careertuner.example.com/api/auth/oauth/naver/callback");
    }

    private static CareerTunerProperties mockEnabled(String apiBaseUrl) {
        CareerTunerProperties properties = new CareerTunerProperties();
        properties.getOauth().setMockEnabled(true);
        properties.getApp().setApiBaseUrl(apiBaseUrl);
        return properties;
    }
}
