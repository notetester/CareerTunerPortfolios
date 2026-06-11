package com.careertuner.interview.training.dto;

/** 파인튜닝 잡 트리거 결과. */
public record FineTuneResponse(
        int sampleCount,
        String baseModel,
        String fileId,
        String jobId,
        String status) {
}
