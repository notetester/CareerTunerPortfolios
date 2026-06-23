package com.careertuner.support.chatbot;

/**
 * searchFaq 툴의 매칭 FAQ 1건. linkUrl/linkLabel 은 faq 테이블에 저장된 실제 값(있을 수 있음).
 * 응답 링크 접지에 사용 — 모델이 만든 게 아니라 DB 출처라 신뢰 가능.
 */
public record FaqHit(String question, String answer, String linkUrl, String linkLabel) {}
