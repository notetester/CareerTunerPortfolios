package com.careertuner.fitanalysis.dto;

import java.util.List;

/**
 * 적합도 분석 응답의 review-first evidence gate 안전 블록(C 소유, R3).
 *
 * <p>공통 {@code ApiResponse} record 는 바꾸지 않고, 적합도 응답 DTO 안에 안전 정보를 옵셔널로 싣는다.
 * 과거(R3 이전) 분석에는 gate 결과가 없으므로 {@code safety} 는 null 일 수 있고, 프런트는 없으면 기존대로 동작한다.
 *
 * <p>gate 는 점수/applyDecision 을 바꾸지 않는다. 이 블록은 노출·검토 상태만 전달한다.
 */
public record FitSafetyResponse(
        String gateStatus,
        boolean needsHumanReview,
        String maxSeverity,
        List<Reason> gateReasons,
        String evidenceGateVersion,
        boolean ragRuntimeEnabled,
        boolean rewriteApplied
) {

    public record Reason(String type, String claim, String reason, String severity) {
    }
}
