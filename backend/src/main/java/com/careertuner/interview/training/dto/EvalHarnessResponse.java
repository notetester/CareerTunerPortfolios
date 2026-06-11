package com.careertuner.interview.training.dto;

/**
 * 평가 하니스 결과 (LLM-as-judge 재채점과 저장 점수의 일치도).
 * meanAbsDiff 가 낮을수록, agreementRate 가 높을수록 채점이 일관적이다.
 */
public record EvalHarnessResponse(
        int evaluated,
        double meanAbsDiff,
        double agreementRate) {
}
