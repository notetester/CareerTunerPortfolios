package com.careertuner.ai.chat;

import java.util.List;

/**
 * 챗봇 이전 대화 복원 응답 (UI 표시용).
 * <p>주의: messages 는 LLM 메모리 윈도우(최근 N개)를 평탄화한 것이라 "전체 이력"이 아니다.
 * 또 links/quickReplies 는 매 턴 휘발성으로 파생되어 메모리에 저장되지 않으므로 복원되지 않는다(텍스트만).
 *
 * @param conversationId 이어서 대화할 대화 id
 * @param messages       역할/텍스트 메시지 목록 (시간순)
 */
public record ChatHistoryResponse(
        Long conversationId,
        List<ChatHistoryMessage> messages
) {
    /** @param role "user" | "bot" */
    public record ChatHistoryMessage(String role, String text) {}
}
