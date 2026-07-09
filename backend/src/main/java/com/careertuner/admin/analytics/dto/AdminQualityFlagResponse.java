package com.careertuner.admin.analytics.dto;

import java.time.LocalDateTime;

/**
 * 분석 품질 검수 큐 항목. 최신 적합도 분석을 결정적 휴리스틱으로 점검해
 * 점수-근거 불일치, 과도한 자격증 추천, 전략 누락 등을 표시한다.
 */
public record AdminQualityFlagResponse(
        Long fitAnalysisId,
        Long applicationCaseId,
        String userName,
        String userEmail,
        String companyName,
        String jobTitle,
        Integer fitScore,
        String flagType,
        String severity,
        String detail,
        LocalDateTime analyzedAt
) {
}
