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
        CareerAnalysisRunResponse analysisRun
) {
}
