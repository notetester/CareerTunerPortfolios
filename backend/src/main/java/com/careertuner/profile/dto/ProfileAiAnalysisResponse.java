package com.careertuner.profile.dto;

import java.util.List;

/**
 * 저장된 프로필 AI 분석 산출물(A영역) 조회 응답 — 사용자가 최근 분석 결과를 새로고침 후에도 볼 수 있게 한다.
 * 요약/강점/약점은 요약 분석(PROFILE_SUMMARY), 완성도 점수·항목은 완성도 진단(PROFILE_COMPLETENESS)에서 온다.
 *
 * @param hasAnalysis       분석 이력 존재 여부(false 면 아직 분석하지 않음 — 부재를 실패로 오독하지 않게)
 * @param summary           프로필 요약
 * @param strengths         강점
 * @param gaps              보완점
 * @param recommendations   개선 추천
 * @param extractedSkills   추출된 보유 역량
 * @param jobFamily         추정 직군(enum 이름)
 * @param jobFamilyLabel    추정 직군 표시 라벨
 * @param completenessScore 완성도 점수(0~100)
 * @param aiScore           AI 종합 점수
 * @param criteria          항목별 점수
 * @param qualityWarnings   품질 경고
 * @param analyzedAt        마지막 분석 시각
 */
public record ProfileAiAnalysisResponse(
        boolean hasAnalysis,
        String summary,
        List<String> strengths,
        List<String> gaps,
        List<String> recommendations,
        List<String> extractedSkills,
        String jobFamily,
        String jobFamilyLabel,
        Integer completenessScore,
        Integer aiScore,
        List<ProfileCriterionScoreResponse> criteria,
        List<String> qualityWarnings,
        Long profileVersionId,
        Integer profileVersionNo,
        String analyzedAt) {

    public static ProfileAiAnalysisResponse empty() {
        return new ProfileAiAnalysisResponse(false, null, List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, List.of(), List.of(), null, null, null);
    }
}
