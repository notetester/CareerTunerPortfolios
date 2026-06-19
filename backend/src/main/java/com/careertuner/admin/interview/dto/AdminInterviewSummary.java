package com.careertuner.admin.interview.dto;

/** 면접 관리 대시보드 통계 — 전체 세션 / 평균 총점 / AI 실패 / 음성·영상 분석 수. */
public record AdminInterviewSummary(
        long totalSessions,
        Integer avgScore,
        long aiFailures,
        long mediaCount) {
}
