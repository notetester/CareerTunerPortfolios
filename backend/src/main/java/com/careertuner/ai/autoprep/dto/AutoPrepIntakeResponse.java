package com.careertuner.ai.autoprep.dto;

import java.util.List;

import com.careertuner.ai.autoprep.PrepPlan;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;

/**
 * 인테이크(멀티턴) 응답.
 * ready=true 면 plan 그대로 /run. false 면 nextAsk 가 가리키는 슬롯을 message + 선택지로 되묻는다.
 *
 * <p>nextAsk: "CASE"(지원 건 — candidates 제공) | "MODE"(면접 모드 — modes 제공)
 *   | "EXTRACTING"(공고 첨부로 지원 건을 만들고 회사·직무 추출 중 — applicationCaseId 제공, 클라가 폴링) | null(ready).
 * applicationCaseId: EXTRACTING 응답에서 갓 만든(또는 추출 진행 중인) 지원 건 id. stateless 멀티턴이라 클라가 다음 턴에 재전송한다.
 */
public record AutoPrepIntakeResponse(
    PrepPlan plan,
    boolean ready,
    String message,
    String nextAsk,
    List<ApplicationCaseResponse> candidates,
    List<ModeOption> modes,
    Long applicationCaseId
) {
    /** applicationCaseId 미사용 응답 호환(null). 기존 6-arg 호출부는 수정 없이 컴파일된다. */
    public AutoPrepIntakeResponse(PrepPlan plan, boolean ready, String message, String nextAsk,
                                  List<ApplicationCaseResponse> candidates, List<ModeOption> modes) {
        this(plan, ready, message, nextAsk, candidates, modes, null);
    }

    /** 면접 모드 선택지(code=백엔드 mode 값, label=표시명). */
    public record ModeOption(String code, String label) {
    }
}
