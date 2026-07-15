package com.careertuner.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 헬스체크 — liveness 는 항상 UP, readiness 는 DB 왕복 성공/실패로 200/503 을 가른다. */
class HealthControllerTest {

    private final HealthMapper healthMapper = mock(HealthMapper.class);
    private final HealthController controller = new HealthController(healthMapper);

    @Test
    void livenessAlwaysUp() {
        assertThat(controller.health().data().get("status")).isEqualTo("UP");
    }

    @Test
    void readinessUpWhenDbPingsSucceeds() {
        when(healthMapper.ping()).thenReturn(1);
        var response = controller.ready();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data())
                .containsEntry("status", "UP")
                .containsEntry("db", "UP");
    }

    @Test
    void readinessDownWhenDbUnavailable() {
        when(healthMapper.ping()).thenThrow(new RuntimeException("connection refused"));
        var response = controller.ready();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().success()).isFalse();
    }
}
