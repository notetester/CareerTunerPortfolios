package com.careertuner.admin.chatbot.dto;

import java.util.List;

/**
 * 공백→발생 대화 드릴(운영 패널 3단계-2 / F3-B).
 * 공백 군집 대표 질문이 실제로 나온 대화를 보여준다.
 * <p>미스 턴은 LLM 메모리에 없을 수 있으므로 화면 핵심은 <b>질문 원문 + 폴백 문구</b>이고,
 * messages_json 이 있으면 방어적으로 파싱해 주변 맥락(contextTurns)만 보조한다(없거나 깨져도 화면은 정상).
 *
 * @param conversationId 발생 대화 id(없으면 null)
 * @param question       공백으로 수집된 질문 원문
 * @param fallbackMessage 그때 챗봇이 돌려준 폴백 안내 문구(고정)
 * @param contextTurns   대화 메모리에서 복원한 주변 맥락(user/bot 텍스트만, 비면 빈 리스트)
 */
public record ChatbotConversationDrillResponse(
        Long conversationId,
        String question,
        String fallbackMessage,
        List<DrillTurn> contextTurns) {

    /**
     * 대화 한 턴(표시용).
     * @param role "user" 또는 "bot"
     * @param text 평문 본문(bot 은 마크다운 제거됨)
     */
    public record DrillTurn(String role, String text) {}
}
