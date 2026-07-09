package com.careertuner.admin.chatbot.dto;

/**
 * FAQ 초안 생성 결과(운영 패널 2단계). 저장하지 않고 반환만 한다 — 운영자가 검토·수정 후 등록.
 * @param question  FAQ 질문 후보(수집된 원문 — 운영자가 다듬는다)
 * @param answer    LLM 이 생성한 FAQ 답변 초안 본문
 * @param frequency 같은 질문 반복 빈도(우선순위 판단용)
 */
public record FaqDraftResponse(String question, String answer, long frequency) {}
