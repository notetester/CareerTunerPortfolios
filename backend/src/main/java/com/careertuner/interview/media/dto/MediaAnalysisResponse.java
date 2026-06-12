package com.careertuner.interview.media.dto;

import java.time.LocalDateTime;

import tools.jackson.databind.JsonNode;

/** 저장된 음성/영상 면접 분석 결과 (JSON 컬럼은 파싱해서 내려준다). */
public record MediaAnalysisResponse(
        Long id,
        Long interviewSessionId,
        String kind,
        JsonNode transcript,
        JsonNode metrics,
        Integer score,
        JsonNode scoreDetail,
        LocalDateTime createdAt) {
}
