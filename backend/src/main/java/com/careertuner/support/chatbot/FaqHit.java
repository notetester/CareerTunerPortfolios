package com.careertuner.support.chatbot;

/**
 * searchFaq 툴의 매칭 FAQ 1건. linkUrl/linkLabel 은 faq 테이블에 저장된 실제 값(있을 수 있음).
 * 응답 링크 접지에 사용 — 모델이 만든 게 아니라 DB 출처라 신뢰 가능.
 * <p>{@code score} = 질의와의 코사인 유사도(임베딩 게이트 판정·진단 로그용). 같은 임베딩 1회에서 산출돼
 * 게이트 결정과 답변 추출이 동일 점수를 공유한다(2회 임베딩 = 새 비결정 틈을 만들지 않음).
 */
public record FaqHit(String question, String answer, String linkUrl, String linkLabel, double score) {}
