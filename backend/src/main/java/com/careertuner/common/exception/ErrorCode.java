package com.careertuner.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 도메인 전반에서 재사용하는 표준 에러 코드.
 * 새 도메인 에러가 필요하면 여기에 추가한다.
 */
@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하거나 충돌하는 요청입니다."),
    INSUFFICIENT_CREDIT(HttpStatus.PAYMENT_REQUIRED, "크레딧이 부족합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
