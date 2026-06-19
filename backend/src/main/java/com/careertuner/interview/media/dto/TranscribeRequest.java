package com.careertuner.interview.media.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 자체 STT 요청 (B 베이직 면접 답변 전사). audioBase64 는 녹음 원본(webm 등) base64.
 */
public record TranscribeRequest(
        @NotBlank String audioBase64,
        String audioFormat,
        String language) {
}
