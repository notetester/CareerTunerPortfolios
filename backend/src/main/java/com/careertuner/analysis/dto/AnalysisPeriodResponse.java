package com.careertuner.analysis.dto;

import java.time.LocalDateTime;

/**
 * 분석 대상 기간과 데이터 수 — 디자인 분석 §6.10(분석 대상 기간과 데이터 수).
 * 분석 결과가 없으면 from/to는 null이다.
 */
public record AnalysisPeriodResponse(
        LocalDateTime from,
        LocalDateTime to,
        int applicationCount,
        int analyzedCount,
        int interviewSessionCount
) {
}
