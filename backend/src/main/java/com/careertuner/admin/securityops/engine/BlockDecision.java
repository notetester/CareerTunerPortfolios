package com.careertuner.admin.securityops.engine;

/**
 * 차단 평가 결과. {@code blocked=false} 면 통과(ALLOW 화이트리스트 매칭 포함).
 */
public record BlockDecision(
        boolean blocked,
        String blockKind,     // IP / USER
        String matchType,     // SINGLE_IP / CIDR / RANGE / COUNTRY / ASN / ACCOUNT_STATUS
        String targetKey,     // 매칭된 규칙 값(로그·응답 근거)
        Long ruleId,
        String reason) {

    private static final BlockDecision ALLOW = new BlockDecision(false, null, null, null, null, null);

    public static BlockDecision allow() {
        return ALLOW;
    }

    public static BlockDecision blockIp(ActiveBlockRule rule, String matchType) {
        return new BlockDecision(true, "IP", matchType,
                rule.getRuleType() + ":" + rule.getRuleValue(), rule.getId(),
                rule.getReason() != null ? rule.getReason() : "보안 정책에 의해 접근이 제한되었습니다.");
    }
}
