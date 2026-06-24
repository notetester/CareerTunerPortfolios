package com.careertuner.admin.chatbot.service;

import java.time.LocalDate;

import com.careertuner.admin.chatbot.dto.ChatbotReferencePage;
import com.careertuner.common.security.AuthUser;

/**
 * 참조 대화 표(F3-A, 답한 대화 로그) 조회. chatbot_response_log where faq_referenced=1.
 * /api/admin/chatbot/references. 디자인 av-table(시각/질문/FAQ/유사도/결과)와 1:1.
 */
public interface AdminChatbotReferenceService {

    /**
     * 답한 대화 로그 페이지 조회.
     * @param from 조회 시작일(일 inclusive, null 이면 to 기준 최근 7일 — 메트릭과 동일 규칙)
     * @param to   조회 종료일(일 inclusive, null 이면 오늘)
     * @param page 0-base 페이지 번호
     * @param size 페이지 크기(1~100)
     */
    ChatbotReferencePage getReferences(AuthUser authUser, LocalDate from, LocalDate to, int page, int size);
}
