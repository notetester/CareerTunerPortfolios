package com.careertuner.interview.media.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 음성 감정 분석 요청. 프런트가 녹음을 16kHz mono PCM16(LINEAR16) 으로 변환해 base64 로 보낸다.
 * (원본 오디오는 분석에만 쓰고 서버에 저장하지 않는다 — ADR-002)
 */
public record VoiceAnalysisRequest(
        @NotBlank String audioBase64,
        Integer sampleRateHertz,
        String language) {
}
