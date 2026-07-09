package com.careertuner.ai.intake.dto;

import java.util.List;

import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse.ModeOption;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;

/**
 * 인테이크 챗봇 응답 그릇.
 *
 * @param conversationId  이어 쓸 대화 id(클라이언트가 다음 턴에 그대로 회신)
 * @param message         에이전트 답변 본문
 * @param ready           슬롯이 다 차서 바로 실행 가능한지(D 의 intake 판정)
 * @param nextAsk         아직 부족하면 무엇을 물어야 하는지("CASE"|"MODE"|null)
 * @param autoPrepRequest 코드가 검증·확정한 값으로 조립한 그릇. ready=true 면 클라이언트가
 *                        이걸로 D 의 {@code POST /api/auto-prep/run/stream} 을 직접 연다.
 * @param candidates      nextAsk="CASE" 일 때 지원 건 후보(칩 렌더용). 그 외 빈 리스트.
 * @param modes           nextAsk="MODE" 일 때 면접 모드 선택지(칩 렌더용). 그 외 빈 리스트.
 */
public record IntakeAskResponse(
        Long conversationId,
        String message,
        boolean ready,
        String nextAsk,
        AutoPrepRequest autoPrepRequest,
        List<ApplicationCaseResponse> candidates,
        List<ModeOption> modes
) {}
