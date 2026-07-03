package com.careertuner.admin.fitanalysis.dto;

import java.util.List;
import java.util.Map;

/**
 * gate 통계 응답 — 운영 gate reason 분포 관측용(차기 model-card 개정·alias 후보 발굴의 전제).
 *
 * <p>분포 맵은 건수 내림차순 LinkedHashMap 이고, gate/review/severity 컬럼이 NULL 인 행은 해당 맵에서 제외한다.
 * gate_reasons_json 이 깨진 행은 집계를 중단하지 않고 {@code brokenReasonsJsonCount} 로만 센다.
 */
public record AdminGateStatsResponse(
        long total,
        Map<String, Long> byGateStatus,
        Map<String, Long> byReviewStatus,
        Map<String, Long> byMaxSeverity,
        Map<String, Long> byReasonType,
        Map<String, Long> byReasonSeverity,
        long brokenReasonsJsonCount,
        // 자주 단정되는 claim 상위 10건(건수 내림차순, 동수는 알파벳순) — alias 후보 발굴용.
        List<TopClaim> topClaims
) {

    public record TopClaim(String claim, long count) {
    }
}
