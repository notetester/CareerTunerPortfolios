package com.careertuner.ai.chat;

import java.util.List;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.chat.ChatResponse.SiteLink;

/**
 * 챗봇 에이전트 응답. conversationId 를 함께 내려 클라이언트가 다음 턴에 재사용한다.
 *
 * @param route  통합 라우팅 진단 라벨(①/③/확인반환/NAV 등). 프런트 비필수 메타.
 * @param intake ③ 인테이크 입구로 보낸 턴에서만 non-null(그 외 null).
 */
public record ChatAskResponse(
        Long conversationId,
        String message,
        List<SiteLink> links,
        List<String> quickReplies,
        String route,
        IntakeStep intake
) {
    /** ③ 인테이크 한 턴 결과 메타(ready/nextAsk/조립된 AutoPrepRequest). sticky 모드는 다음 PR. */
    public record IntakeStep(boolean ready, String nextAsk, AutoPrepRequest autoPrepRequest) {}
}
