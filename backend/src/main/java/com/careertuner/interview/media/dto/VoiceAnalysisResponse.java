package com.careertuner.interview.media.dto;

import tools.jackson.databind.JsonNode;

/**
 * Inworld voice profiling 결과.
 * voiceProfile: {age|emotion|pitch|vocalStyle|accent: [{label, confidence}]} — 신뢰도 낮으면 필드가 빠질 수 있다.
 */
public record VoiceAnalysisResponse(
        String transcript,
        JsonNode voiceProfile) {
}
