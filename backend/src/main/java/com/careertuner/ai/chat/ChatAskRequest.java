package com.careertuner.ai.chat;

/**
 * 챗봇 에이전트 요청. conversationId 가 null 이면 새 대화로 발급한다.
 *
 * @param selectedCaseId   ③ 인테이크에서 지원 건 칩을 직접 고른 경우의 caseId(그 외 null). 누락 시 null — 하위호환.
 * @param selectedModeCode ③ 인테이크에서 면접 모드 버튼을 직접 고른 경우의 code(그 외 null). 누락 시 null — 하위호환.
 */
public record ChatAskRequest(String question, Long conversationId,
                             Long selectedCaseId, String selectedModeCode) {}
