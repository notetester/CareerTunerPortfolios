package com.careertuner.analysis.ai.provider;

public record CareerAnalysisAiUsage(
        String model,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        boolean mock
) {
    public static CareerAnalysisAiUsage mockUsage() {
        return new CareerAnalysisAiUsage("mock", 0, 0, 0, true);
    }
}
