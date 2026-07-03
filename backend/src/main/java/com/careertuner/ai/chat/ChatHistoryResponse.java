package com.careertuner.ai.chat;

import java.util.List;

/**
 * 챗봇 이전 대화 복원 응답 (UI 표시용).
 * <p>주의: messages 는 LLM 메모리 윈도우(최근 N개)를 평탄화한 것이라 "전체 이력"이 아니다.
 * 또 links/quickReplies 는 매 턴 휘발성으로 파생되어 메모리에 저장되지 않으므로 복원되지 않는다(텍스트만).
 *
 * @param conversationId 이어서 대화할 대화 id
 * @param messages       역할/텍스트 메시지 목록 (시간순)
 * @param resume         ④ 온보딩 진행 중 대화 복원 시 "현재 스텝 재표시" 프롬프트(아니면 null).
 *                       ④턴은 설계상 LLM 메모리에 기록되지 않아(완료 시 요약 1줄만 주입) messages 만으로는
 *                       재진입 사용자가 무엇을 답해야 하는지 알 수 없다 — 이 프롬프트가 그 공백을 메운다.
 */
public record ChatHistoryResponse(
        Long conversationId,
        List<ChatHistoryMessage> messages,
        ResumePrompt resume
) {
    /** resume 없는 기존 호출부(세션 메시지 조회 등)용. */
    public ChatHistoryResponse(Long conversationId, List<ChatHistoryMessage> messages) {
        this(conversationId, messages, null);
    }

    /** @param role "user" | "bot" */
    public record ChatHistoryMessage(String role, String text) {}

    /**
     * ④ 재진입 재개 프롬프트 — 복원 직후 봇 메시지로 이어붙일 현재 스텝 안내.
     * route 는 프론트 가이드 매핑(ONB_ROUTE_PHASE) 키, intake 는 AWAIT_MODE 재표시의 모드 칩용.
     */
    public record ResumePrompt(String route, String message, List<String> quickReplies,
                               ChatAskResponse.IntakeStep intake) {}
}
