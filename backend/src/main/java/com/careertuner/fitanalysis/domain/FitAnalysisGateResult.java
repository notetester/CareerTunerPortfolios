package com.careertuner.fitanalysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 적합도 분석 1건의 review-first evidence gate 결정(C 소유, fit_analysis_gate_result 1:1).
 *
 * <p>gate 는 점수/applyDecision 을 바꾸지 않고 노출·검토 상태만 기록한다. gateReasons 는 축약 JSON(개인정보·원문 제외).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitAnalysisGateResult {

    private Long id;
    private Long fitAnalysisId;
    private String gateStatus;
    private boolean needsHumanReview;
    private int reasonCount;
    private String maxSeverity;
    private String gateReasonsJson;
    private String evidenceGateVersion;
    private boolean ragRuntimeEnabled;
    private boolean rewriteApplied;
    private LocalDateTime createdAt;
}
