package com.careertuner.admin.securityops.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 차단 매칭 엔진 회귀 테스트 — "규칙이 실제로 차단하는가"를 증명한다.
 * CIDR/RANGE/COUNTRY/ASN 실매칭, ALLOW 화이트리스트 우선, IP 정규화, REVIEW 비차단을 검증한다.
 */
class BlockRuleMatcherTest {

    private final BlockRuleMatcher matcher = new BlockRuleMatcher();

    private static ActiveBlockRule rule(String type, String value, String action, int priority) {
        return new ActiveBlockRule(1L, type, value, action, "GLOBAL", priority, "테스트 규칙", "MANUAL");
    }

    @Test
    void singleIp_exactMatch_blocks() {
        var rules = List.of(rule("IP", "10.0.0.5", "BLOCK", 100));
        assertThat(matcher.evaluate("10.0.0.5", null, null, rules).blocked()).isTrue();
        assertThat(matcher.evaluate("10.0.0.6", null, null, rules).blocked()).isFalse();
    }

    @Test
    void cidr_subnetMatch_blocksInsideRange() {
        var rules = List.of(rule("CIDR", "192.168.1.0/24", "BLOCK", 100));
        assertThat(matcher.evaluate("192.168.1.1", null, null, rules).blocked()).isTrue();
        assertThat(matcher.evaluate("192.168.1.254", null, null, rules).blocked()).isTrue();
        assertThat(matcher.evaluate("192.168.2.1", null, null, rules).blocked()).isFalse();
        BlockDecision d = matcher.evaluate("192.168.1.50", null, null, rules);
        assertThat(d.matchType()).isEqualTo("CIDR");
    }

    @Test
    void ipRange_inclusiveBounds_block() {
        var rules = List.of(rule("IP_RANGE", "10.0.0.10~10.0.0.20", "BLOCK", 100));
        assertThat(matcher.evaluate("10.0.0.10", null, null, rules).blocked()).isTrue(); // 하한 포함
        assertThat(matcher.evaluate("10.0.0.20", null, null, rules).blocked()).isTrue(); // 상한 포함
        assertThat(matcher.evaluate("10.0.0.15", null, null, rules).blocked()).isTrue();
        assertThat(matcher.evaluate("10.0.0.21", null, null, rules).blocked()).isFalse();
        assertThat(matcher.evaluate("10.0.0.9", null, null, rules).blocked()).isFalse();
    }

    @Test
    void country_matchesNormalizedHeader() {
        var rules = List.of(rule("COUNTRY", "KR", "BLOCK", 100));
        assertThat(matcher.evaluate("1.2.3.4", "KR", null, rules).blocked()).isTrue();
        assertThat(matcher.evaluate("1.2.3.4", "kr", null, rules).blocked()).isTrue(); // 대소문자 무관
        assertThat(matcher.evaluate("1.2.3.4", "US", null, rules).blocked()).isFalse();
        assertThat(matcher.evaluate("1.2.3.4", "XX", null, rules).blocked()).isFalse(); // 미상 국가는 매칭 안 함
    }

    @Test
    void asn_normalizesAsPrefix() {
        var rules = List.of(rule("ASN", "12345", "BLOCK", 100));
        assertThat(matcher.evaluate("1.2.3.4", null, "AS12345", rules).blocked()).isTrue();
        assertThat(matcher.evaluate("1.2.3.4", null, "12345", rules).blocked()).isTrue();
        assertThat(matcher.evaluate("1.2.3.4", null, "AS9999", rules).blocked()).isFalse();
    }

    @Test
    void allowlist_takesPrecedence_overBlockRule() {
        // 우선순위 높은 ALLOW 가 먼저 평가돼 화이트리스트로 통과시킨다.
        var rules = List.of(
                rule("IP", "1.2.3.4", "ALLOWLIST", 200),
                rule("CIDR", "1.2.3.0/24", "BLOCK", 100));
        assertThat(matcher.evaluate("1.2.3.4", null, null, rules).blocked()).isFalse();
        // 화이트리스트에 없는 같은 대역 IP 는 여전히 차단된다.
        assertThat(matcher.evaluate("1.2.3.99", null, null, rules).blocked()).isTrue();
    }

    @Test
    void reviewAndChallenge_doNotBlockRequest() {
        assertThat(matcher.evaluate("1.2.3.4", null, null,
                List.of(rule("IP", "1.2.3.4", "REVIEW", 100))).blocked()).isFalse();
        assertThat(matcher.evaluate("1.2.3.4", null, null,
                List.of(rule("IP", "1.2.3.4", "CHALLENGE", 100))).blocked()).isFalse();
    }

    @Test
    void ipv6Loopback_normalizedToIpv4() {
        var rules = List.of(rule("IP", "127.0.0.1", "BLOCK", 100));
        assertThat(matcher.evaluate("::1", null, null, rules).blocked()).isTrue();
        assertThat(matcher.evaluate("0:0:0:0:0:0:0:1", null, null, rules).blocked()).isTrue();
    }

    @Test
    void emptyRules_allowAll() {
        assertThat(matcher.evaluate("1.2.3.4", "KR", "AS1", List.of()).blocked()).isFalse();
        assertThat(matcher.evaluate("1.2.3.4", "KR", "AS1", null).blocked()).isFalse();
    }

    @Test
    void userAndEmailRules_notEvaluatedForRequestBlocking() {
        // USER/EMAIL 규칙은 요청 차단 대상이 아니라 매칭에서 제외된다.
        var rules = List.of(rule("EMAIL", "redacted-970907fd1e409029@example.com", "BLOCK", 100), rule("USER", "42", "BLOCK", 100));
        assertThat(matcher.evaluate("1.2.3.4", "KR", "AS1", rules).blocked()).isFalse();
    }
}
