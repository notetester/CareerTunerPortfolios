package com.careertuner.interview.media.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 음성 점수 요청 — 자체 추론 서버(serve)로 전달한다 (ADR-006).
 *
 * <p>{@code audioBase64} 는 녹음 원본(webm/wav 등) base64. 글자수·군말수·응답지연은
 * 프런트가 라이브 트랜스크립트/타이머에서 계산해 함께 보낸다(녹음 파일만으론 알 수 없는 값).
 */
public record VoiceScoreRequest(
        @NotBlank String audioBase64,
        String audioFormat,
        Integer transcriptChars,
        Integer fillerCount,
        Double latencySec) {
}
