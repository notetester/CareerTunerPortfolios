package com.careertuner.applicationcase.service;

/**
 * OCR 결과 + 귀속 정보. Claude/OpenAI OCR 이 "텍스트만"이 아니라 "누가(provider)·어떤 모델·사용량"을 함께 들고 오게 한다.
 *
 * <p>{@code strategy}(PDF_TEXT 등)는 sourceType 기반이라 엔진 식별에 못 쓴다 — 귀속은 이 {@code provider} 로만.
 * provider 예: {@code claude|openai|worker|pdfbox}.
 */
public record OcrPayload(String text, String provider, String model, AiUsage usage) {
}
