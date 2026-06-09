package com.careertuner.dashboard.ai;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

/**
 * 대시보드 AI 분석 결과 요약(18). 홈/대시보드 상단에 바로 노출할 한 문단 요약이다.
 */
public record DashboardInsightAiResult(
        String summary,
        CareerAnalysisAiUsage usage,
        String status,
        String errorMessage,
        boolean retryable
) {
}
