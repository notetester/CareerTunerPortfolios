package com.careertuner.profile.dto;

/**
 * 구조화 분석 결과(초안). DB 에 자동 커밋하지 않는다 — 사용자 승인 후 PUT /profile.
 *
 * @param jobId    비동기 작업 id (동기 완료 시에도 동일 형식)
 * @param status   PENDING | DONE | FAILED
 * @param draft    DONE 일 때 추출 초안 (education/career/projects/skills/portfolioLinks)
 * @param errorMessage FAILED 시 사용자 메시지
 */
public record ProfileAnalyzeResponse(
        String jobId,
        String status,
        ProfileAnalyzeDraft draft,
        String errorMessage
) {
    public static ProfileAnalyzeResponse pending(String jobId) {
        return new ProfileAnalyzeResponse(jobId, "PENDING", null, null);
    }

    public static ProfileAnalyzeResponse done(String jobId, ProfileAnalyzeDraft draft) {
        return new ProfileAnalyzeResponse(jobId, "DONE", draft, null);
    }

    public static ProfileAnalyzeResponse failed(String jobId, String errorMessage) {
        return new ProfileAnalyzeResponse(jobId, "FAILED", null, errorMessage);
    }
}
