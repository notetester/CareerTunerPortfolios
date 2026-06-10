package com.careertuner.interview.training.dto;

/** 학습 데이터 현황. */
public record TrainingStatsResponse(
        long sampleCount,
        Double averageScore) {
}
