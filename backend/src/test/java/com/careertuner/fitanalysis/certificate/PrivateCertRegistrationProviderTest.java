package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class PrivateCertRegistrationProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private PrivateCertRegistrationProvider provider(String key) {
        return new PrivateCertRegistrationProvider(key, "https://unused.invalid",
                "uddi:test", "20251231", Duration.ofSeconds(1), mapper, HttpClient.newHttpClient());
    }

    @Test
    void registeredActiveWhenCurrentStatusRegistered() {
        String json = "{\"currentCount\":1,\"matchCount\":1,\"data\":[{"
                + "\"자격명\":\"빅데이터전문가\",\"등록번호\":\"2020-001234\",\"현재상태\":\"등록완료\","
                + "\"신청기관\":\"한국빅데이터학회\",\"공인여부\":\"등록\",\"등급공인여부\":\"N\"}]}";
        PrivateCertRegistrationEvidence e = provider("k").parse(json, "빅데이터");

        assertThat(e.status()).isEqualTo(PrivateCertRegistrationStatus.REGISTERED_ACTIVE);
        assertThat(e.matchCount()).isEqualTo(1);
        assertThat(e.matches()).hasSize(1);
        assertThat(e.matches().get(0).name()).isEqualTo("빅데이터전문가");
        assertThat(e.matches().get(0).institution()).isEqualTo("한국빅데이터학회");
        assertThat(e.snapshot()).isEqualTo("20251231");
    }

    @Test
    void abolishedOnlyWhenAllStatusesAbolished() {
        String json = "{\"matchCount\":1,\"data\":[{"
                + "\"자격명\":\"폐지된자격\",\"등록번호\":\"2009-9\",\"현재상태\":\"폐지\","
                + "\"신청기관\":\"어느기관\",\"공인여부\":\"N\"}]}";
        PrivateCertRegistrationEvidence e = provider("k").parse(json, "폐지된자격");

        assertThat(e.status()).isEqualTo(PrivateCertRegistrationStatus.ABOLISHED_OR_CANCELLED);
    }

    @Test
    void notFoundWhenNoMatches() {
        PrivateCertRegistrationEvidence e = provider("k").parse("{\"data\":[],\"matchCount\":0}", "없는자격");

        assertThat(e.status()).isEqualTo(PrivateCertRegistrationStatus.NOT_FOUND);
        assertThat(e.matches()).isEmpty();
    }

    @Test
    void malformedBodyDegradesToUpstreamUnavailable() {
        PrivateCertRegistrationEvidence e = provider("k").parse("<<not json>>", "자격");

        assertThat(e.status()).isEqualTo(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void blankKeyDoesNotCallApiAndDegrades() {
        PrivateCertRegistrationProvider p = provider("");

        assertThat(p.enabled()).isFalse();
        assertThat(p.lookup("빅데이터").status()).isEqualTo(PrivateCertRegistrationStatus.UPSTREAM_UNAVAILABLE);
    }
}
