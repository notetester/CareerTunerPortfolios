package com.careertuner.ai.autoprep.dto;

import com.careertuner.ai.autoprep.PrepPlan;
import com.careertuner.ai.autoprep.PrepStepResult;

import java.util.List;

/**
 * AI 오케스트레이터 실행 결과. plan(두뇌가 세운 계획) + 단계별 결과 목록.
 */
public record AutoPrepResponse(
    PrepPlan plan,
    List<PrepStepResult> steps,
    String message
) {
}
