package com.careertuner.admin.fitanalysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFitAnalysisResult {

    private Long id;
    private Long applicationCaseId;
    private Long userId;
    private String userName;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private String applicationStatus;
    private boolean favorite;
    private Integer fitScore;
    private String matchedSkills;
    private String missingSkills;
    private String recommendedStudy;
    private String recommendedCertificates;
    private String strategy;
    private String sourceSnapshot;
    private String scoreBasis;
    private String gapRecommendations;
    private String certificateRecommendations;
    private String strategyActions;
    private String conditionMatrix;
    private String analysisConfidence;
    private String applyDecision;
    private String model;
    private String promptVersion;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private int memoCount;
    private LocalDateTime latestMemoAt;
    private boolean reanalysisRequested;

    // review-first evidence gate(R3) — LEFT JOIN fit_analysis_gate_result. R3 이전 분석은 NULL.
    private String gateStatus;
    private Boolean gateNeedsHumanReview;
    private Integer gateReasonCount;
    private String gateMaxSeverity;
    private String evidenceGateVersion;
    // 축약 gate reason 목록 JSON([{type,claim,reason,severity}]). 상세에서만 파싱해 노출(개인정보·원문 제외).
    private String gateReasonsJson;
    // gate review workflow(운영자 처리 상태): PENDING/RESOLVED/REANALYSIS_REQUESTED. gate 없으면 NULL.
    private String gateReviewStatus;
    private java.time.LocalDateTime gateReviewedAt;
    private String gateReviewerName;
}
