package com.careertuner.ai.chat;

/**
 * 챗봇 에이전트 요청. conversationId 가 null 이면 새 대화로 발급한다.
 *
 * @param selectedCaseId   ③ 인테이크에서 지원 건 칩을 직접 고른 경우의 caseId(그 외 null). 누락 시 null — 하위호환.
 * @param selectedModeCode ③ 인테이크에서 면접 모드 버튼을 직접 고른 경우의 code(그 외 null). 누락 시 null — 하위호환.
 * @param faqChip          빈 화면 FAQ 추천 칩을 클릭한 턴이면 true. 깡통계정 온보딩 게이트 "첫 진입"만 그 턴에
 *                         한해 우회한다(게이트가 FAQ 질문을 삼키는 문제 — 자유 텍스트 분류 완화는 실측에서
 *                         인테이크 회귀라 기각, 결정적 칩 신호만 통과). 누락 시 null — 하위호환.
 */
public record ChatAskRequest(String question, Long conversationId,
                             Long selectedCaseId, String selectedModeCode,
                             Boolean faqChip) {}
