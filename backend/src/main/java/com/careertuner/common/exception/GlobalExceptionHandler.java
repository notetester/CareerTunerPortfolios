package com.careertuner.common.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.careertuner.common.web.ApiResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 전 컨트롤러 공통 예외 처리. 모든 에러를 {@link ApiResponse} 형태로 통일한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("BusinessException: {} - {}", code.name(), ex.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null
                ? "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage())
                : ErrorCode.INVALID_INPUT.getDefaultMessage();
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.name(), message));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            BindException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception ex) {
        log.debug("Invalid request: {}", ex.getClass().getSimpleName());
        ErrorCode code = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getDefaultMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getDefaultMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        ErrorCode code = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getDefaultMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        String message = "업로드 파일 크기가 허용 범위를 초과했습니다. 화면에 표시된 제한 용량 이하의 파일을 사용해 주세요.";
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.name(), message));
    }

    /**
     * DB 연결/리소스 장애(연결 획득 실패·통신 링크 유실)를 503 으로 매핑한다.
     *
     * <p>프론트 outage 폴백이 503 을 upstream 장애 후보로 잡아 readiness 로 재확인한 뒤 mock 데모로
     * 전환하도록 하는 신호다. 제약위반·SQL 문법오류(DataIntegrityViolationException·
     * BadSqlGrammarException)는 이 타입 계층이 아니므로 여전히 500(INTERNAL_ERROR)로 남아
     * 실제 버그가 outage 로 오인되지 않는다.</p>
     */
    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            TransientDataAccessResourceException.class,
    })
    public ResponseEntity<ApiResponse<Void>> handleDbUnavailable(DataAccessException ex) {
        ErrorCode code = ErrorCode.SERVICE_UNAVAILABLE;
        log.warn("DB 리소스 장애(연결 불가 추정) → 503: {}", ex.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getDefaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getDefaultMessage()));
    }
}
