package com.careertuner.interview.media.dto;

/**
 * 자체 STT(serve {@code /transcribe}) 결과 — B 베이직 면접 답변 전사 (faster-whisper, API 0).
 */
public record TranscribeResponse(
        String text,
        String language,
        double duration) {
}
