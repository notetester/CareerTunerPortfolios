package com.careertuner.applicationcase.service;

/**
 * provider 중립 AI 사용량. 공고 OCR 경로(Claude/OpenAI)가 provider 결합 없이 usage 를 들고 다니게 하기 위한 공통 타입.
 * (분석 경로는 기존 {@link OpenAiResponsesClient.Usage} 를 계속 쓴다 — 범위 최소화.)
 */
public record AiUsage(String model, int inputTokens, int outputTokens, int totalTokens) {

    /** OpenAI usage → 공통 AiUsage 변환(어댑터). null 이면 null. */
    public static AiUsage from(OpenAiResponsesClient.Usage usage) {
        if (usage == null) {
            return null;
        }
        return new AiUsage(usage.model(), usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
    }
}
