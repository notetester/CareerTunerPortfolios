package com.careertuner.admin.chatbot.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * chatbot_response_log 일자별 집계점(스파크라인 소스). MyBatis 매핑용 — record 대신 @Data.
 * <p>answered/handoffs 는 그 날의 건수. 서비스가 카드별로 필요한 쪽을 ChatbotMetricPoint 로 펴고
 * 빈 날을 0 으로 채운다.
 */
@Data
public class ResponseLogDailyPoint {
    private LocalDate date;
    private long answered;
    private long handoffs;
}
