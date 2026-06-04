package com.careertuner.common.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 나타내는 런타임 예외.
 * {@link GlobalExceptionHandler} 가 {@link ErrorCode} 의 HTTP 상태로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
