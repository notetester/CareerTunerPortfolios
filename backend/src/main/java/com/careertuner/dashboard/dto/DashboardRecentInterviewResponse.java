package com.careertuner.dashboard.dto;

import java.time.LocalDateTime;

/**
 * 대시보드 "최근 면접" 카드(디자인 분석 §6.4: 점수 변화, 핵심 개선점, 리포트 보기).
 * previousScore가 없으면 scoreDelta는 null이다.
 */
public record DashboardRecentInterviewResponse(
        Long sessionId,
        Long applicationCaseId,
        String companyName,
        String jobTitle,
        String mode,
        Integer totalScore,
        Integer previousScore,
        Integer scoreDelta,
        String keyImprovement,
        LocalDateTime occurredAt
) {
}
