package com.careertuner.companyanalysis.dto;

import java.time.LocalDateTime;

import com.careertuner.companyanalysis.domain.CompanyAnalysis;

public record CompanyAnalysisResponse(
        Long id,
        Long applicationCaseId,
        Long jobPostingId,
        Integer jobPostingRevision,
        String companySummary,
        String recentIssues,
        String industry,
        String competitors,
        String interviewPoints,
        String sources,
        String verifiedFacts,
        String aiInferences,
        String unknowns,
        String sourceType,
        LocalDateTime checkedAt,
        LocalDateTime refreshRecommendedAt,
        LocalDateTime confirmedAt,
        String adminMemo,
        String requestedProvider,
        String actualProvider,
        String actualModel,
        Boolean fallbackUsed,
        String attemptPath,
        String runMode,
        LocalDateTime createdAt
) {
    /**
     * unknowns 는 DB 컬럼 없이 저장된 aiInferences 의 {@code kind=UNKNOWN} 마커를 응답 직전
     * 펼친 virtual 필드다. 호출부(서비스)가 마커 분리를 수행해 aiInferences(마커 제거본)와
     * unknowns 를 함께 넘긴다 — 프런트/하네스가 마커를 직접 파싱하지 않게 하기 위함이다.
     */
    public static CompanyAnalysisResponse from(CompanyAnalysis analysis, String aiInferences, String unknowns) {
        if (analysis == null) {
            return null;
        }
        return new CompanyAnalysisResponse(
                analysis.getId(),
                analysis.getApplicationCaseId(),
                analysis.getJobPostingId(),
                analysis.getJobPostingRevision(),
                analysis.getCompanySummary(),
                analysis.getRecentIssues(),
                analysis.getIndustry(),
                analysis.getCompetitors(),
                analysis.getInterviewPoints(),
                analysis.getSources(),
                analysis.getVerifiedFacts(),
                aiInferences,
                unknowns,
                analysis.getSourceType(),
                analysis.getCheckedAt(),
                analysis.getRefreshRecommendedAt(),
                analysis.getConfirmedAt(),
                analysis.getAdminMemo(),
                analysis.getRequestedProvider(),
                analysis.getActualProvider(),
                analysis.getActualModel(),
                analysis.getFallbackUsed(),
                analysis.getAttemptPath(),
                analysis.getRunMode(),
                analysis.getCreatedAt());
    }
}
