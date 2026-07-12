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
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 콘텐츠 형식입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "서비스 이용에 필요한 동의가 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하거나 충돌하는 요청입니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
    REFUND_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "환불 정책상 신청할 수 없는 결제입니다."),
    INSUFFICIENT_CREDIT(HttpStatus.PAYMENT_REQUIRED, "크레딧이 부족합니다."),
    PAYMENT_CONFIRM_FAILED(HttpStatus.BAD_GATEWAY, "결제 승인에 실패했습니다."),
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 초안 생성에 일시적으로 실패했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "현재 사용할 수 없는 기능입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
