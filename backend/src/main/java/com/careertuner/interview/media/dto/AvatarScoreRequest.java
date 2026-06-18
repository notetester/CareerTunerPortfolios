package com.careertuner.interview.media.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 아바타 화상면접 점수 요청 — 자체 추론 서버(serve)로 전달한다 (ADR-006/ADR-007).
 *
 * <p>{@code videoBase64} 는 녹화 원본(webm 등) base64. serve 가 webm 1개에서
 * 음성(ffmpeg)·영상(MediaPipe) 피처를 함께 뽑아 late fusion 점수를 낸다. 글자수·군말수·
 * 응답지연은 프런트가 라이브 트랜스크립트/타이머에서 계산해 함께 보낸다.
 */
public record AvatarScoreRequest(
        @NotBlank String videoBase64,
        String videoFormat,
        Integer transcriptChars,
        Integer fillerCount,
        Double latencySec) {
}
