package com.careertuner.interview.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * 음성/영상 면접 분석 결과 저장 요청. 온디바이스 분석이라 점수·지표(JSON)만 받는다.
 *
 * @param kind        VOICE / AVATAR
 * @param transcript  [{"role":"ai|user","text":"..."}]
 * @param metrics     측정 지표 원본 (말속도·침묵·필러·피치, 표정/자세, Inworld 프로필 등)
 * @param score       종합 점수 0~100
 * @param scoreDetail 항목별 점수
 */
public record SaveMediaAnalysisRequest(
        @NotBlank String kind,
        JsonNode transcript,
        JsonNode metrics,
        @NotNull Integer score,
        JsonNode scoreDetail) {
}
