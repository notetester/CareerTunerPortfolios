package com.careertuner.common.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.web.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/** Spring Security 필터 단계의 오류도 공통 {@link ApiResponse} 형식으로 기록한다. */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void unauthorized(HttpServletResponse response) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    public void forbidden(HttpServletResponse response) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    public void serviceUnavailable(HttpServletResponse response) throws IOException {
        write(response, ErrorCode.SERVICE_UNAVAILABLE);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.getStatus().value());
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(errorCode.name(), errorCode.getDefaultMessage()));
    }
}
