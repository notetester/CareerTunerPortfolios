package com.careertuner.ai.autoprep.dto;

import java.util.List;

import com.careertuner.ai.autoprep.PrepPlan;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;

/**
 * 인테이크(멀티턴) 응답.
 * ready=true 면 plan 그대로 /run. false 면 nextAsk 가 가리키는 슬롯을 message + 선택지로 되묻는다.
 *
 * <p>nextAsk: "CASE"(지원 건 — candidates 제공) | "MODE"(면접 모드 — modes 제공) | null(ready).
 */
public record AutoPrepIntakeResponse(
    PrepPlan plan,
    boolean ready,
    String message,
    String nextAsk,
    List<ApplicationCaseResponse> candidates,
    List<ModeOption> modes
) {
    /** 면접 모드 선택지(code=백엔드 mode 값, label=표시명). */
    public record ModeOption(String code, String label) {
    }
}
