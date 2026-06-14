package com.careertuner.home.dto;

/** 시작 준비(온보딩) 단계. 대시보드 집계에서 파생한 결정적 완료 여부를 담는다. */
public record HomeOnboardingStepResponse(
        String key,
        String label,
        boolean done
) {
}
