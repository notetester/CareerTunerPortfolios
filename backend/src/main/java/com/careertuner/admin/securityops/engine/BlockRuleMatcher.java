package com.careertuner.admin.securityops.engine;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 애플리케이션 레벨 차단 규칙 매칭 엔진.
 *
 * <p>TripTogether {@code IpBlockInterceptor} 의 매칭 로직을 이식했다. 요청의 클라이언트 IP·국가·ASN 을
 * 활성 규칙(우선순위 DESC)과 대조해 첫 매칭을 반환한다. {@code ALLOW}(화이트리스트) 규칙이 먼저
 * 매칭되면 즉시 통과시킨다. 서블릿 의존이 없어 단위 테스트로 검증 가능하다.</p>
 *
 * <p>대량 DDoS 는 앞단 WAF/CDN 이 처리하고, 여기서는 로그인 남용·악성 대역·국가/ASN 차단처럼
 * 애플리케이션 문맥이 필요한 정밀 차단을 캐시 기준으로 즉시 적용한다.</p>
 */
@Component
public class BlockRuleMatcher {

    /** 규칙 목록(우선순위 DESC 정렬 전제)을 순회해 첫 매칭 결정을 낸다. */
    public BlockDecision evaluate(String clientIp, String countryCode, String asn, List<ActiveBlockRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return BlockDecision.allow();
        }
        String ip = normalizeIp(clientIp);
        String country = normalizeCountry(countryCode);
        String normalizedAsn = normalizeAsn(asn);
        for (ActiveBlockRule rule : rules) {
            if (rule == null) {
                continue;
            }
            String matchType = matchedType(ip, country, normalizedAsn, rule);
            if (matchType == null) {
                continue;
            }
            if ("ALLOWLIST".equalsIgnoreCase(rule.getActionType()) || "ALLOW".equalsIgnoreCase(rule.getActionType())) {
                return BlockDecision.allow();
            }
            // BLOCK 만 실제 차단한다. REVIEW/CHALLENGE 는 인입 자체를 막지 않고 통과시킨다(별도 처리 대상).
            if ("BLOCK".equalsIgnoreCase(rule.getActionType())) {
                return BlockDecision.blockIp(rule, matchType);
            }
        }
        return BlockDecision.allow();
    }

    /** 규칙이 이 요청과 매칭되면 매칭 타입 문자열을, 아니면 null 을 반환한다. */
    String matchedType(String clientIp, String countryCode, String asn, ActiveBlockRule rule) {
        String ruleType = rule.getRuleType() == null ? "" : rule.getRuleType().trim().toUpperCase(Locale.ROOT);
        String value = rule.getRuleValue();
        return switch (ruleType) {
            case "IP", "SINGLE_IP" -> clientIp != null && clientIp.equals(normalizeIp(value)) ? "SINGLE_IP" : null;
            case "CIDR" -> matchesCidr(clientIp, value) ? "CIDR" : null;
            case "IP_RANGE", "RANGE" -> matchesRange(clientIp, value) ? "RANGE" : null;
            case "COUNTRY" -> matchesCountry(countryCode, value) ? "COUNTRY" : null;
            case "ASN" -> matchesAsn(asn, value) ? "ASN" : null;
            default -> null; // USER/EMAIL/EMAIL_DOMAIN 은 요청 차단 대상이 아님
        };
    }

    boolean matchesCidr(String clientIp, String cidr) {
        if (clientIp == null || cidr == null || cidr.isBlank()) {
            return false;
        }
        try {
            String[] parts = cidr.trim().split("/");
            if (parts.length != 2) {
                return false;
            }
            InetAddress client = InetAddress.getByName(clientIp);
            InetAddress subnet = InetAddress.getByName(parts[0].trim());
            int prefix = Integer.parseInt(parts[1].trim());
            byte[] clientBytes = client.getAddress();
            byte[] subnetBytes = subnet.getAddress();
            if (clientBytes.length != subnetBytes.length) {
                return false; // IPv4 vs IPv6 불일치
            }
            int totalBits = clientBytes.length * 8;
            if (prefix < 0 || prefix > totalBits) {
                return false;
            }
            BigInteger clientInt = new BigInteger(1, clientBytes);
            BigInteger subnetInt = new BigInteger(1, subnetBytes);
            BigInteger mask = prefix == 0
                    ? BigInteger.ZERO
                    : BigInteger.ONE.shiftLeft(totalBits).subtract(BigInteger.ONE)
                        .shiftRight(totalBits - prefix)
                        .shiftLeft(totalBits - prefix);
            return clientInt.and(mask).equals(subnetInt.and(mask));
        } catch (Exception e) {
            return false;
        }
    }

    /** rule_value = "startIp~endIp" (또는 '-' 구분). */
    boolean matchesRange(String clientIp, String rangeValue) {
        if (clientIp == null || rangeValue == null || rangeValue.isBlank()) {
            return false;
        }
        String[] parts = rangeValue.contains("~") ? rangeValue.split("~") : rangeValue.split("-", 2);
        if (parts.length != 2) {
            return false;
        }
        try {
            BigInteger client = ipToBigInteger(clientIp);
            BigInteger start = ipToBigInteger(parts[0].trim());
            BigInteger end = ipToBigInteger(parts[1].trim());
            if (client == null || start == null || end == null) {
                return false;
            }
            if (start.compareTo(end) > 0) {
                BigInteger tmp = start;
                start = end;
                end = tmp;
            }
            return client.compareTo(start) >= 0 && client.compareTo(end) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    boolean matchesCountry(String requestCountryCode, String ruleValue) {
        String requestCountry = normalizeCountry(requestCountryCode);
        String ruleCountry = normalizeCountry(ruleValue);
        return requestCountry != null && requestCountry.equals(ruleCountry);
    }

    boolean matchesAsn(String requestAsn, String ruleValue) {
        String req = normalizeAsn(requestAsn);
        String ruleAsn = normalizeAsn(ruleValue);
        return req != null && req.equals(ruleAsn);
    }

    BigInteger ipToBigInteger(String ip) {
        try {
            return new BigInteger(1, InetAddress.getByName(normalizeIp(ip)).getAddress());
        } catch (Exception e) {
            return null;
        }
    }

    /** IPv6 루프백/매핑 정규화(::1 → 127.0.0.1, ::ffff: 접두 제거). */
    public String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ("0:0:0:0:0:0:0:1".equals(trimmed) || "::1".equals(trimmed)) {
            return "127.0.0.1";
        }
        if (trimmed.startsWith("::ffff:")) {
            return trimmed.substring(7);
        }
        return trimmed;
    }

    public String normalizeCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        if ("XX".equals(normalized) || "UNKNOWN".equals(normalized)) {
            return null;
        }
        return normalized.length() > 2 ? normalized.substring(0, 2) : normalized;
    }

    public String normalizeAsn(String asn) {
        if (asn == null || asn.isBlank()) {
            return null;
        }
        String normalized = asn.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("AS")) {
            normalized = normalized.substring(2);
        }
        normalized = normalized.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }
}
