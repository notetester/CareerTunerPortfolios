package com.careertuner.activitylog.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.careertuner.activitylog.domain.UserSecurityHistory;
import com.careertuner.activitylog.mapper.SecurityHistoryMapper;
import com.careertuner.activitylog.web.ActivityLogInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 민감 계정 이벤트(아이디찾기/비번재설정/이메일·전화 인증 등) 감사 기록.
 *
 * <p>인증 흐름에서 호출한다. 현재 요청 컨텍스트에서 IP/UA/requestId 를 자동 보강하고,
 * 실패해도 인증 흐름을 절대 깨지 않는다(best-effort). TripTogether {@code recordSecurityEvent} 이식.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityHistoryService {

    private final SecurityHistoryMapper mapper;

    /** 부분 채운 이벤트를 받아 요청 컨텍스트로 보강 후 저장. */
    public void record(UserSecurityHistory event) {
        if (event == null) {
            return;
        }
        try {
            enrichFromRequest(event);
            mapper.insertSecurityHistory(event);
        } catch (Exception e) {
            log.warn("[SecurityHistory] 보안 이력 저장 실패(type={}): {}", event.getEventType(), e.getMessage());
        }
    }

    /** 간편 기록 — 자주 쓰는 필드만. */
    public void record(String eventType, String eventStage, Long userId, boolean success,
                       String inputIdentifier, String failReason) {
        record(UserSecurityHistory.builder()
                .eventType(eventType)
                .eventStage(eventStage)
                .userId(userId)
                .success(success)
                .inputIdentifier(inputIdentifier)
                .failReason(failReason)
                .build());
    }

    private void enrichFromRequest(UserSecurityHistory event) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        if (event.getIpAddress() == null) {
            event.setIpAddress(getClientIp(request));
        }
        if (event.getUserAgent() == null) {
            String ua = request.getHeader("User-Agent");
            event.setUserAgent(ua == null ? null : (ua.length() > 512 ? ua.substring(0, 512) : ua));
        }
        if (event.getRequestId() == null) {
            Object rid = request.getAttribute(ActivityLogInterceptor.ATTR_REQUEST_ID);
            if (rid != null) {
                event.setRequestId(rid.toString());
                if (event.getFlowTraceId() == null) {
                    event.setFlowTraceId(rid.toString());
                }
            }
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (Exception ignored) {
            // 비웹 컨텍스트(스케줄러 등)에서는 요청이 없다
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP"};
        for (String h : headers) {
            String ip = request.getHeader(h);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
