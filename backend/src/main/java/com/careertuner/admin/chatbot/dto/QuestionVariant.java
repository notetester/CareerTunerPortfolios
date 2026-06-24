package com.careertuner.admin.chatbot.dto;

/**
 * 군집에 묶인 변형 질문 1개와 그 건수(운영 화면 "묶인 사용자 질문" 칩).
 */
public record QuestionVariant(String question, long count) {}
