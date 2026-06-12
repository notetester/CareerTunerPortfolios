package com.careertuner.analysis.dto;

import java.util.List;

public record AnalysisSummaryResponse(
        AnalysisStatResponse stats,
        List<SkillGapResponse> skillGaps,
        List<JobReadinessResponse> jobReadiness,
        List<AnalysisScorePointResponse> scoreHistory,
        List<AnalysisApplicationSummaryResponse> applications,
        List<String> recommendedDirections,
        String trendSummary,
        InterviewTrendResponse interviewTrend,
        // 아래는 결정적 집계(기획 §8.9, 디자인 분석 §6.10). AI 캐시 fingerprint에는 포함하지 않는다.
        List<AnalysisStrengthTrendResponse> strengthTrends,
        List<AnalysisJobDistributionResponse> jobDistribution,
        List<AnalysisAnswerThemeResponse> answerThemes,
        AnalysisPeriodResponse period,
        List<AnalysisMonthlyFitResponse> monthlyFitTrend,
        List<AnalysisApplicationTierResponse> applicationTiers,
        List<AnalysisSkillFitResponse> skillFitAverages,
        List<AnalysisFitInterviewBandResponse> fitInterviewBands,
        List<AnalysisApplicationPriorityResponse> applicationPriorities,
        List<AnalysisCareerRiskResponse> careerRisks,
        CareerAnalysisRunResponse analysisRun
) {
}
