package com.careertuner.admin.securityops.engine;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.careertuner.admin.securityops.engine.BlockRuleCacheService.BlockRuleCacheSnapshot;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 애플리케이션 레벨 IP/국가/ASN 차단 인터셉터.
 *
 * <p>런타임 캐시({@link BlockRuleCacheService})의 활성 규칙으로 매 요청을 평가하고, 차단이면
 * 컨트롤러 진입 전에 403(JSON)으로 되돌린다. 앞단 WAF/CDN 이 없는 배포에서도 앱이 스스로
 * 정밀 차단을 집행하는 계층이다. TripTogether {@code IpBlockInterceptor} 를 SPA(JSON) 로 이식했다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpBlockInterceptor implements HandlerInterceptor {

    private final BlockRuleCacheService blockRuleCacheService;
    private final BlockRuleMatcher matcher;
    private final BlockEngineMapper blockEngineMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = matcher.normalizeIp(getClientIp(request));
        String countryCode = resolveCountryCode(request);
        String asn = resolveAsn(request);

        BlockRuleCacheSnapshot snapshot = blockRuleCacheService.getSnapshot();
        BlockDecision decision = matcher.evaluate(clientIp, countryCode, asn, snapshot.getRules());
        if (!decision.blocked()) {
            return true;
        }

        writeBlockedResponse(request, response, decision, clientIp, countryCode, asn, snapshot.getSource());
        return false;
    }

    private void writeBlockedResponse(HttpServletRequest request, HttpServletResponse response,
                                      BlockDecision decision, String clientIp, String countryCode,
                                      String asn, String cacheSource) throws Exception {
        String requestId = UUID.randomUUID().toString();
        logBlocked(request, decision, requestId, clientIp, countryCode, asn, cacheSource);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-Block-Request-Id", requestId);
        ApiResponse<Void> body = ApiResponse.error("ACCESS_BLOCKED",
                decision.reason() != null ? decision.reason() : "보안 정책에 의해 접근이 제한되었습니다.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void logBlocked(HttpServletRequest request, BlockDecision decision, String requestId,
                            String clientIp, String countryCode, String asn, String cacheSource) {
        try {
            blockEngineMapper.insertBlockAccessLog(BlockAccessLogEntry.builder()
                    .requestId(requestId)
                    .userId(currentUserId())
                    .requestUri(request.getRequestURI())
                    .httpMethod(request.getMethod())
                    .blockKind(decision.blockKind())
                    .blockMatchType(decision.matchType())
                    .blockTargetKey(decision.targetKey())
                    .blockRuleId(decision.ruleId())
                    .blockReason(decision.reason())
                    .clientIp(clientIp)
                    .countryCode(countryCode)
                    .asn(asn)
                    .cacheSource(cacheSource)
                    .userAgent(truncate(request.getHeader("User-Agent"), 500))
                    .build());
        } catch (Exception e) {
            log.warn("[BlockAccessLog] 차단 요청 로그 저장 실패: {}", e.getMessage());
        }
    }

    private Long currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
                return user.id();
            }
        } catch (Exception ignored) {
            // 인증 컨텍스트가 아직 없거나(비로그인) 형태가 다르면 익명 차단으로 로깅
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR", "X-Real-IP"
        };
        for (String h : headers) {
            String ip = request.getHeader(h);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String resolveCountryCode(HttpServletRequest request) {
        return matcher.normalizeCountry(firstNonBlank(
                request.getHeader("CF-IPCountry"),
                request.getHeader("CloudFront-Viewer-Country"),
                request.getHeader("X-Country-Code"),
                request.getHeader("X-App-Country-Code")));
    }

    private String resolveAsn(HttpServletRequest request) {
        return matcher.normalizeAsn(firstNonBlank(
                request.getHeader("CF-ASN"),
                request.getHeader("X-ASN"),
                request.getHeader("X-App-ASN")));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
