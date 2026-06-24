package com.careertuner.admin.chatbot.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 메트릭 스파크라인의 일자별 점(일자, 카운트).
 * MyBatis 가 직접 매핑(no-arg + setter)하고, 서비스가 빈 날짜를 0 으로 채워 연속 시계열로 만든다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotMetricPoint {
    private LocalDate date;
    private long count;
}
