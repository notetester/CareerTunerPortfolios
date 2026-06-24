package com.careertuner.ai.intake.dto;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;

/**
 * 인테이크 챗봇 응답 그릇.
 *
 * @param conversationId  이어 쓸 대화 id(클라이언트가 다음 턴에 그대로 회신)
 * @param message         에이전트 답변 본문
 * @param ready           슬롯이 다 차서 바로 실행 가능한지(D 의 intake 판정)
 * @param nextAsk         아직 부족하면 무엇을 물어야 하는지("CASE"|"MODE"|null)
 * @param autoPrepRequest 코드가 검증·확정한 값으로 조립한 그릇. ready=true 면 클라이언트가
 *                        이걸로 D 의 {@code POST /api/auto-prep/run/stream} 을 직접 연다.
 */
public record IntakeAskResponse(
        Long conversationId,
        String message,
        boolean ready,
        String nextAsk,
        AutoPrepRequest autoPrepRequest
) {}
