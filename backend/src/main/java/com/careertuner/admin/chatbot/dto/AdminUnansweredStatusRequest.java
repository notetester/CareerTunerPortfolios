package com.careertuner.admin.chatbot.dto;

/**
 * 답 못한 질문 그룹 상태변경 요청.
 * status 는 운영 처리값(REVIEWED/DISMISSED)만 허용 — 검증은 서비스에서.
 */
public record AdminUnansweredStatusRequest(String status) {}
