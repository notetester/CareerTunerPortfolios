package com.careertuner.analysis.ai;

import java.util.List;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

/**
 * 장기 취업 경향(16) 요약과 다음 지원 방향(17) 추천 결과.
 */
public record CareerTrendAiResult(
        String trendSummary,
        List<String> recommendedDirections,
        CareerAnalysisAiUsage usage,
        String status,
        String errorMessage,
        boolean retryable
) {
}
