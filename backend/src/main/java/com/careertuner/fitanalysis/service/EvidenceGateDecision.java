package com.careertuner.fitanalysis.service;

import java.util.List;

/**
 * review-first evidence gate 결정(R3).
 *
 * <p>적합도 분석 AI 설명 출력의 결정론 후처리 결과다. gate 는 점수/applyDecision/matchedSkills/missingSkills 를
 * <b>바꾸지 않고</b> 노출·검토 상태만 기록한다. RAG runtime 자동주입과 rewrite 자동노출은 이 단계에서 하지 않는다
 * (reports/60 결론 · {@code docs/archive/2026-06/c-evidence-gate-r3-pre-design.md} 비목표).
 *
 * @param gateStatus       PASSED / REVIEW_REQUIRED / REJECTED
 * @param needsHumanReview REVIEW_REQUIRED·REJECTED 면 true
 * @param maxSeverity      reasons 중 최고 심각도(warning/critical) — 없으면 null
 * @param reasons          unsupported user-owned claim 목록(축약, 개인정보·원문 미포함)
 * @param evidenceSources  gate 가 사용한 evidence 버킷 스냅샷(감사·재현용)
 */
public record EvidenceGateDecision(
        String gateStatus,
        boolean needsHumanReview,
        String maxSeverity,
        List<Reason> reasons,
        List<EvidenceSource> evidenceSources
) {

    /** gate 정책 버전(롤백/재현 추적용). */
    public static final String VERSION = "r3-review-first";
    /** 이 단계에서 RAG runtime 자동 통합은 보류(항상 false). */
    public static final boolean RAG_RUNTIME_ENABLED = false;
    /** 이 단계에서 rewrite 자동 노출은 보류(항상 false). */
    public static final boolean REWRITE_APPLIED = false;

    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String SEVERITY_WARNING = "warning";
    public static final String SEVERITY_CRITICAL = "critical";

    /**
     * unsupported user-owned claim 1건.
     *
     * @param type     requirement_as_owned / catalog_as_owned / unsupported / structural
     * @param claim    문제 스킬명/축약(개인정보·원문 prompt 미포함)
     * @param reason   사람이 읽을 사유(축약)
     * @param severity warning / critical
     */
    public record Reason(String type, String claim, String reason, String severity) {
    }

    /**
     * gate 가 판단에 사용한 evidence 버킷 1개.
     *
     * @param sourceType userEvidence / jobRequirements / catalogFacts / companyContext
     * @param userOwned  지원자 보유 근거인지(userEvidence 만 true)
     * @param items      축약 스킬/근거 목록(원문·개인정보 제외)
     */
    public record EvidenceSource(String sourceType, boolean userOwned, List<String> items) {
    }
}
