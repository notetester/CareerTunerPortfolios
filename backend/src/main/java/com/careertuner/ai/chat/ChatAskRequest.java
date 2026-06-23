package com.careertuner.ai.chat;

/**
 * 챗봇 에이전트 요청. conversationId 가 null 이면 새 대화로 발급한다.
 */
public record ChatAskRequest(String question, Long conversationId) {}
