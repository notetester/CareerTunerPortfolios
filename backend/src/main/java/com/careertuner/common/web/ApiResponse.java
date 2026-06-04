package com.careertuner.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 REST 응답을 감싸는 표준 envelope.
 *
 * <pre>
 * 성공: { "success": true,  "code": "OK",          "data": { ... } }
 * 실패: { "success": false, "code": "NOT_FOUND",  "message": "..." }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", null, data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, "OK", null, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
