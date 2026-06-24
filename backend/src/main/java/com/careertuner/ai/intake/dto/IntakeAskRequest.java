package com.careertuner.ai.intake.dto;

/**
 * 인테이크 챗봇 요청 그릇. conversationId 가 null 이면 새 대화를 발급한다.
 *
 * @param message        사용자 발화
 * @param conversationId 이어가는 대화 id(없으면 신규)
 */
public record IntakeAskRequest(String message, Long conversationId) {}
