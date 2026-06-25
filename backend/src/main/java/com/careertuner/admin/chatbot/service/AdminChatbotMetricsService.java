package com.careertuner.admin.chatbot.service;

import java.time.LocalDate;

import com.careertuner.admin.chatbot.dto.ChatbotMetricsResponse;
import com.careertuner.common.security.AuthUser;

/**
 * AI 상담 운영 콘솔 메트릭 밴드(3단계-2).
 * S1: FAQ 공백 카드(미해결 질문 군집 수 + 기간 시계열). 나머지 3카드는 후속 단계.
 */
public interface AdminChatbotMetricsService {

    /**
     * 메트릭 밴드 조회.
     * @param from 조회 시작일(일 inclusive, null 이면 to 기준 최근 7일)
     * @param to   조회 종료일(일 inclusive, null 이면 오늘)
     */
    ChatbotMetricsResponse getMetrics(AuthUser authUser, LocalDate from, LocalDate to);
}
