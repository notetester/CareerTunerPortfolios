package com.careertuner.admin.chatbot.dto;

import lombok.Data;

/**
 * chatbot_response_log 기간 집계(메트릭 밴드 분자/분모). MyBatis 매핑용 — record 대신 @Data.
 * <p>total = 전 턴 수(분모), answered = FAQ 근거 응답 수(자동 해결 분자),
 * handoffs = 상담사/문의 전환 수(전환율 분자).
 */
@Data
public class ResponseLogAggregate {
    private long total;
    private long answered;
    private long handoffs;
}
