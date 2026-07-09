package com.careertuner.support.chatbot;

/**
 * 코사인 유사도 계산. 외부 라이브러리 없이 직접 구현.
 */
public final class CosineSimilarity {

    private CosineSimilarity() {}

    public static double compute(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("벡터 차원 불일치: " + a.length + " vs " + b.length);
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0.0) {
            return 0.0;
        }
        return dot / denominator;
    }
}
