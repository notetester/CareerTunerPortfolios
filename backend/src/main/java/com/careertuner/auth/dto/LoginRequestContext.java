package com.careertuner.auth.dto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Request metadata used by auth services for audit logging.
 * This keeps HTTP-specific parsing in the controller layer and policy logic in the service layer.
 */
public record LoginRequestContext(
        String ipAddress,
        String userAgent,
        String requestUri) {

    public static LoginRequestContext from(HttpServletRequest request) {
        if (request == null) {
            return new LoginRequestContext(null, null, null);
        }
        return new LoginRequestContext(resolveClientIp(request), trimToNull(request.getHeader("User-Agent")),
                trimToNull(request.getRequestURI()));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = trimToNull(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return trimToNull(forwardedFor.split(",")[0]);
        }
        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        return realIp != null ? realIp : trimToNull(request.getRemoteAddr());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
