package com.careertuner.reward.dto;

/**
 * 리워드 적립 시도 결과. applied=false 면 규칙 미존재/off/일일 캡 초과 등으로 미적립.
 * skipReason: NO_RULE / DISABLED / DAILY_CAP / NOTHING_TO_GRANT / null(적립됨).
 */
public record RewardGrantResult(
        String eventCode,
        boolean applied,
        int pointsGranted,
        int creditGranted,
        boolean leveledUp,
        int levelBefore,
        int levelAfter,
        String skipReason
) {
    public static RewardGrantResult skipped(String eventCode, String reason) {
        return new RewardGrantResult(eventCode, false, 0, 0, false, 0, 0, reason);
    }
}
