package com.careertuner.activitylog.web;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.careertuner.activitylog.domain.UserActivityLog;
import com.careertuner.activitylog.mapper.ActivityLogMapper;
import com.careertuner.common.security.AuthUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 일반 활동 로그 인터셉터 — 정적 리소스를 제외한 모든 /api 요청을 자동 기록(비회원 포함).
 *
 * <p>누가/어디를/어떤 방식으로 호출했는지, 성공 여부·응답시간까지 남긴다. 보안 이력이 계정/인증 민감
 * 이벤트를 다룬다면, 이 인터셉터는 서비스 전반의 활동 흐름을 넓게 기록한다. TripTogether
 * {@code ActivityLogInterceptor} 를 JWT/SPA(SecurityContext 기반 사용자 해석) 로 이식했다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLogInterceptor implements HandlerInterceptor {

    public static final String ATTR_REQUEST_ID = "activityLog.requestId";
    public static final String ATTR_START_TIME = "activityLog.startTime";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "token", "password", "newPassword", "currentPassword", "code", "state", "refreshToken", "accessToken");

    private final ActivityLogMapper activityLogMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_REQUEST_ID, UUID.randomUUID().toString());
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            String uri = request.getRequestURI();
            String method = request.getMethod();
            String requestId = (String) request.getAttribute(ATTR_REQUEST_ID);
            Long startTime = (Long) request.getAttribute(ATTR_START_TIME);
            Integer responseTimeMs = startTime == null ? null
                    : Math.toIntExact(Math.max(0L, System.currentTimeMillis() - startTime));
            Integer status = response.getStatus();
            boolean success = ex == null && status != null && status < 400;

            String handlerName = null;
            if (handler instanceof HandlerMethod hm) {
                handlerName = hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();
            }
            String activityCode = resolveActivityCode(uri, method, handlerName);

            activityLogMapper.insertActivityLog(UserActivityLog.builder()
                    .requestId(requestId)
                    .flowTraceId(requestId)
                    .userId(currentUserId())
                    .sessionId(null)
                    .requestUri(truncate(uri, 512))
                    .httpMethod(method)
                    .activityDomain(resolveActivityDomain(uri, handlerName))
                    .activityType(resolveActivityType(request))
                    .activityCode(activityCode)
                    .activityProvider(resolveActivityProvider(uri))
                    .authEventType(resolveAuthEventType(uri, activityCode))
                    .targetType(resolveTargetType(uri))
                    .targetId(resolveTargetId(uri))
                    .handlerName(handlerName)
                    .queryString(truncate(sanitizeQueryString(request.getQueryString()), 1000))
                    .referer(truncate(sanitizeReferer(request.getHeader("Referer")), 512))
                    .ipAddress(getClientIp(request))
                    .userAgent(truncate(request.getHeader("User-Agent"), 512))
                    .responseStatus(status)
                    .responseTimeMs(responseTimeMs)
                    .success(success)
                    .detailSummary(truncate(buildDetailSummary(activityCode, handlerName, request), 500))
                    .build());
        } catch (Exception loggingEx) {
            log.warn("[ActivityLog] 활동 로그 저장 실패: {}", loggingEx.getMessage());
        }
    }

    private Long currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
                return user.id();
            }
        } catch (Exception ignored) {
            // 비로그인/컨텍스트 없음 → 게스트로 기록
        }
        return null;
    }

    private String resolveActivityDomain(String uri, String handlerName) {
        if (uri == null) {
            return "GENERAL";
        }
        if (uri.contains("/api/auth")) return "AUTH";
        if (uri.contains("/api/admin")) return "ADMIN";
        if (uri.contains("/api/community")) return "COMMUNITY";
        if (uri.contains("/api/collaboration") || uri.contains("/api/messenger")) return "MESSENGER";
        if (uri.contains("/api/profile") || uri.contains("/api/nicknames") || uri.contains("/api/me")) return "PROFILE";
        if (uri.contains("/api/support") || uri.contains("/api/tickets") || uri.contains("/api/chatbot")) return "SUPPORT";
        if (uri.contains("/api/interview")) return "INTERVIEW";
        if (uri.contains("/api/applications") || uri.contains("/api/analysis") || uri.contains("/api/correction")) return "APPLICATION";
        if (uri.contains("/api/billing") || uri.contains("/api/payment") || uri.contains("/api/credit")) return "BILLING";
        return "GENERAL";
    }

    private String resolveActivityType(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String xrw = request.getHeader("X-Requested-With");
        if (uri != null && uri.contains("/api/")) {
            return "API";
        }
        if ("XMLHttpRequest".equalsIgnoreCase(xrw)) {
            return "AJAX";
        }
        return "GET".equalsIgnoreCase(request.getMethod()) ? "PAGE_VIEW" : "ACTION";
    }

    private String resolveActivityCode(String uri, String method, String handlerName) {
        if (uri == null) {
            return handlerName;
        }
        if (uri.endsWith("/api/auth/login") && "POST".equalsIgnoreCase(method)) return "SUBMIT_LOGIN";
        if (uri.endsWith("/api/auth/register") && "POST".equalsIgnoreCase(method)) return "SUBMIT_REGISTER";
        if (uri.endsWith("/api/auth/logout")) return "LOGOUT";
        if (uri.endsWith("/api/auth/refresh")) return "REFRESH_TOKEN";
        if (uri.contains("/api/auth/password/reset")) return "RESET_PASSWORD_FLOW";
        if (uri.contains("/api/auth/phone")) return "PHONE_VERIFY_FLOW";
        if (uri.contains("/api/auth/verify-email")) return "EMAIL_VERIFY";
        if (uri.contains("/api/auth/oauth")) return "OAUTH_FLOW";
        if (uri.contains("/api/community") && "POST".equalsIgnoreCase(method)) return "COMMUNITY_WRITE";
        if (uri.contains("/api/admin")) return "ADMIN_ACCESS";
        return handlerName;
    }

    private String resolveActivityProvider(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.contains("kakao")) return "KAKAO";
        if (uri.contains("naver")) return "NAVER";
        if (uri.contains("google")) return "GOOGLE";
        if (uri.contains("/api/auth")) return "LOCAL";
        return null;
    }

    private String resolveAuthEventType(String uri, String activityCode) {
        if (uri == null || !uri.contains("/api/auth")) {
            return null;
        }
        if ((activityCode != null && activityCode.contains("LOGOUT")) || uri.contains("logout")) return "LOGOUT";
        if ((activityCode != null && activityCode.contains("LOGIN")) || uri.contains("login")) return "LOGIN";
        if (uri.contains("oauth")) return "LINK";
        return null;
    }

    private String resolveTargetType(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.contains("/community/posts")) return "POST";
        if (uri.contains("/community/comments")) return "COMMENT";
        if (uri.contains("/tickets") || uri.contains("/support")) return "TICKET";
        if (uri.contains("/interview")) return "INTERVIEW_SESSION";
        if (uri.contains("/applications")) return "APPLICATION_CASE";
        return null;
    }

    private String resolveTargetId(String uri) {
        if (uri == null) {
            return null;
        }
        String[] parts = uri.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i] != null && parts[i].matches("\\d+")) {
                return parts[i];
            }
        }
        return null;
    }

    private String buildDetailSummary(String activityCode, String handlerName, HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        if (activityCode != null) {
            sb.append("code=").append(activityCode);
        }
        if (handlerName != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("handler=").append(handlerName);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String sanitizeQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }
        StringBuilder sb = new StringBuilder();
        for (String pair : queryString.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = kv[0];
            String value = kv.length > 1 ? kv[1] : "";
            if (SENSITIVE_KEYS.contains(key)) {
                value = "***";
            }
            if (sb.length() > 0) sb.append('&');
            sb.append(key);
            if (!value.isEmpty()) sb.append('=').append(value);
        }
        return sb.toString();
    }

    private String sanitizeReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return referer;
        }
        int q = referer.indexOf('?');
        return q < 0 ? referer : referer.substring(0, q) + "?***";
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String h : headers) {
            String ip = request.getHeader(h);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
