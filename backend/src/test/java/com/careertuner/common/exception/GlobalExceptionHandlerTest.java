package com.careertuner.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;

/**
 * DB 연결 장애만 503(→ 프론트 outage 폴백 신호)으로 매핑되고, 제약위반/일반 오류는 500 을
 * 유지하는지 검증한다. 실제 버그가 outage 로 오인되면 안 되기 때문이다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void dbConnectionFailureMapsTo503() {
        var res = handler.handleDbUnavailable(new DataAccessResourceFailureException("connection refused"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().success()).isFalse();
        assertThat(res.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void transientResourceFailureMapsTo503() {
        var res = handler.handleDbUnavailable(new TransientDataAccessResourceException("communications link failure"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void constraintViolationIsNotTreatedAsOutage() {
        // DataIntegrityViolationException 은 handleDbUnavailable 대상이 아니므로 catch-all(500)로 남는다.
        var res = handler.handleUnexpected(new DataIntegrityViolationException("duplicate key"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void genericErrorStays500() {
        var res = handler.handleUnexpected(new RuntimeException("some bug"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
