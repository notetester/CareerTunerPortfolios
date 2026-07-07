package com.careertuner.companyanalysis.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * NAVER_SEARCH_* env → naver.search.* relaxed binding 검증.
 * 공유 application.yaml 수정 없이 env 만으로 키가 바인딩되는 것이 D-1 계약이다.
 * 테스트에는 dummy 값만 쓴다(실키 금지).
 */
class NaverSearchPropertiesTest {

    private NaverSearchProperties bindFromEnv(Map<String, Object> env) {
        SystemEnvironmentPropertySource source =
                new SystemEnvironmentPropertySource("test-system-env", env);
        Binder binder = new Binder(ConfigurationPropertySources.from(source));
        return binder.bind("naver.search", Bindable.of(NaverSearchProperties.class))
                .orElseGet(NaverSearchProperties::new);
    }

    @Test
    void envVariablesBindToClientIdAndSecret() {
        NaverSearchProperties props = bindFromEnv(Map.of(
                "NAVER_SEARCH_CLIENT_ID", "dummy-client-id",
                "NAVER_SEARCH_CLIENT_SECRET", "dummy-client-secret"));

        assertThat(props.getClientId()).isEqualTo("dummy-client-id");
        assertThat(props.getClientSecret()).isEqualTo("dummy-client-secret");
        assertThat(props.configured()).isTrue();
    }

    @Test
    void defaultsApplyWhenEnvMissing() {
        NaverSearchProperties props = bindFromEnv(Map.of());

        assertThat(props.getBaseUrl()).isEqualTo("https://openapi.naver.com/v1/search");
        assertThat(props.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(props.getDisplay()).isEqualTo(10);
        assertThat(props.configured()).isFalse();
    }

    @Test
    void notConfiguredWhenSecretMissing() {
        NaverSearchProperties props = bindFromEnv(Map.of(
                "NAVER_SEARCH_CLIENT_ID", "dummy-client-id"));

        assertThat(props.configured()).isFalse();
    }

    @Test
    void searchUrlJoinsEndpointWithoutDoubleSlash() {
        NaverSearchProperties props = new NaverSearchProperties();
        props.setBaseUrl("https://openapi.naver.com/v1/search/");

        assertThat(props.searchUrl("news.json"))
                .isEqualTo("https://openapi.naver.com/v1/search/news.json");
    }

    /** 시크릿 노출 방지 — toString 이 재정의되어 secret 이 새는 회귀를 막는다. */
    @Test
    void toStringDoesNotExposeSecret() {
        NaverSearchProperties props = new NaverSearchProperties();
        props.setClientId("dummy-client-id");
        props.setClientSecret("dummy-client-secret");

        assertThat(props.toString()).doesNotContain("dummy-client-secret");
    }
}
