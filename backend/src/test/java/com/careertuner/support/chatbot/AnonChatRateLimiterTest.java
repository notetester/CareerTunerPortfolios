package com.careertuner.support.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 익명 챗봇 rate limiter 단위 테스트 — 분 한도 집행, 비활성/미상 통과, IP 추출.
 */
class AnonChatRateLimiterTest {

    private AnonChatRateLimiter limiter(boolean enabled, int perMinute, int perDay) {
        ChatbotProperties props = new ChatbotProperties();
        props.getAnonRateLimit().setEnabled(enabled);
        props.getAnonRateLimit().setPerMinute(perMinute);
        props.getAnonRateLimit().setPerDay(perDay);
        return new AnonChatRateLimiter(props);
    }

    @Test
    void allowsUpToPerMinuteThenBlocks() {
        AnonChatRateLimiter rl = limiter(true, 3, 1000);
        assertThat(rl.tryAcquire("1.1.1.1")).isTrue();
        assertThat(rl.tryAcquire("1.1.1.1")).isTrue();
        assertThat(rl.tryAcquire("1.1.1.1")).isTrue();
        assertThat(rl.tryAcquire("1.1.1.1")).isFalse(); // 4th in same minute → blocked
    }

    @Test
    void perDayCapBlocksEvenUnderMinuteCap() {
        AnonChatRateLimiter rl = limiter(true, 1000, 2);
        assertThat(rl.tryAcquire("2.2.2.2")).isTrue();
        assertThat(rl.tryAcquire("2.2.2.2")).isTrue();
        assertThat(rl.tryAcquire("2.2.2.2")).isFalse(); // 3rd in day → blocked
    }

    @Test
    void separateIpsAreIndependent() {
        AnonChatRateLimiter rl = limiter(true, 1, 1000);
        assertThat(rl.tryAcquire("3.3.3.3")).isTrue();
        assertThat(rl.tryAcquire("3.3.3.3")).isFalse();
        assertThat(rl.tryAcquire("4.4.4.4")).isTrue(); // 다른 IP 는 독립 카운터
    }

    @Test
    void disabledAlwaysPasses() {
        AnonChatRateLimiter rl = limiter(false, 1, 1);
        for (int i = 0; i < 10; i++) {
            assertThat(rl.tryAcquire("5.5.5.5")).isTrue();
        }
    }

    @Test
    void nullOrBlankIpFailsOpen() {
        AnonChatRateLimiter rl = limiter(true, 1, 1);
        assertThat(rl.tryAcquire(null)).isTrue();
        assertThat(rl.tryAcquire(" ")).isTrue();
    }

    @Test
    void clientIpPrefersForwardedForFirstToken() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 10.0.0.2");
        assertThat(AnonChatRateLimiter.clientIp(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void clientIpFallsBackToRealIpThenRemoteAddr() {
        MockHttpServletRequest realIp = new MockHttpServletRequest();
        realIp.addHeader("X-Real-IP", "198.51.100.9");
        assertThat(AnonChatRateLimiter.clientIp(realIp)).isEqualTo("198.51.100.9");

        MockHttpServletRequest remote = new MockHttpServletRequest();
        remote.setRemoteAddr("192.0.2.5");
        assertThat(AnonChatRateLimiter.clientIp(remote)).isEqualTo("192.0.2.5");
    }
}
