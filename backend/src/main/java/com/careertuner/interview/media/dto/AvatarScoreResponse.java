package com.careertuner.interview.media.dto;

import tools.jackson.databind.JsonNode;

/**
 * 자체 추론 서버 아바타 점수 결과 (late fusion, ADR-006/ADR-007).
 *
 * <p>음성·영상을 각각 별 모델로 채점하고 마지막에 결합한다.
 * voice/visual: 각각 {@code {score,detail,metrics,source}} 노드(영상 추출 실패 시 visual=null).
 * combined: 음성·영상 가중 결합 0~100.
 */
public record AvatarScoreResponse(
        JsonNode voice,
        JsonNode visual,
        int combined) {
}
