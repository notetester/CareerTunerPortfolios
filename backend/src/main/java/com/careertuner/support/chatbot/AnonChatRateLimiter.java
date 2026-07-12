package com.careertuner.support.chatbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 익명(비로그인) 챗봇 호출 남용 방어 — 단일 인스턴스 인메모리 per-IP fixed-window 카운터.
 *
 * <p>배경: {@code /api/chatbot/ask}·{@code /api/chatbot/summarize-posts} 는 비로그인도 쓸 수 있도록
 * permitAll 로 열려 있고(의도된 제품 기능), 로그인 사용자는 {@code ChatbotQuotaPolicyService} 의
 * 일일 쿼터로 관리된다. 그런데 익명 hot-path 는 그 쿼터 밖이라 무제한 호출로 생성형 백엔드(4090 Ollama·
 * 상위 LLM 폴백)를 포화시킬 수 있다. 이 컴포넌트가 그 익명 경로만 IP 단위로 완만히 제한한다.</p>
 *
 * <p>기본값은 정상 사용을 막지 않을 만큼 넉넉하며, {@code ai.chatbot.anon-rate-limit.enabled=false}
 * 로 즉시 끌 수 있다. 배포는 단일 EC2 인스턴스라 분산 저장소 없이 인메모리로 충분하다.
 * IP 미상/한도 초과 판정 실패는 통과(fail-open) — 방어는 보조 계층이지 인증이 아니다.</p>
 */
@Component
@RequiredArgsConstructor
public class AnonChatRateLimiter {

    private final ChatbotProperties properties;

    /** IP 별 분/일 두 윈도우 카운터. */
    private final Map<String, Counters> byIp = new ConcurrentHashMap<>();

    /** 인메모리 무한 증가 방지 상한 — 초과 시 당일 아닌 엔트리 정리. */
    private static final int MAX_TRACKED_IPS = 50_000;

    /**
     * 익명 호출 1건을 소비 시도한다.
     *
     * @return 허용이면 true, 분/일 한도 초과면 false. 비활성/ IP 미상이면 언제나 true(통과).
     */
    public boolean tryAcquire(String ip) {
        ChatbotProperties.AnonRateLimit cfg = properties.getAnonRateLimit();
        if (!cfg.isEnabled() || ip == null || ip.isBlank()) {
            return true;
        }
        long nowMs = System.currentTimeMillis();
        long minuteBucket = nowMs / 60_000L;
        long dayBucket = nowMs / 86_400_000L;

        if (byIp.size() > MAX_TRACKED_IPS) {
            byIp.entrySet().removeIf(e -> e.getValue().dayBucket != dayBucket);
        }
        Counters c = byIp.computeIfAbsent(ip, k -> new Counters());
        synchronized (c) {
            if (c.dayBucket != dayBucket) {
                c.dayBucket = dayBucket;
                c.dayCount = 0;
                c.minuteBucket = minuteBucket;
                c.minuteCount = 0;
            } else if (c.minuteBucket != minuteBucket) {
                c.minuteBucket = minuteBucket;
                c.minuteCount = 0;
            }
            if (c.minuteCount >= cfg.getPerMinute() || c.dayCount >= cfg.getPerDay()) {
                return false;
            }
            c.minuteCount++;
            c.dayCount++;
            return true;
        }
    }

    /**
     * 프록시(nginx/EC2) 뒤 실제 클라이언트 IP 추출 — X-Forwarded-For 첫 토큰 우선.
     * 다른 인터셉터(ActivityLog·IpBlock)와 동일 규칙.
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Counters {
        long minuteBucket = -1;
        long dayBucket = -1;
        int minuteCount;
        int dayCount;
    }
}
