package com.careertuner.ai.autoprep.dto;

import java.util.List;

import com.careertuner.ai.autoprep.PrepPlan;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;

/**
 * 인테이크(준비 시작 전 슬롯 확인) 응답.
 * ready=true 면 plan 그대로 /run 가능. false 면 message 로 되묻고 candidates(지원 건 후보) 중 골라 다시 요청한다.
 */
public record AutoPrepIntakeResponse(
    PrepPlan plan,
    boolean ready,
    String message,
    List<ApplicationCaseResponse> candidates
) {
}
