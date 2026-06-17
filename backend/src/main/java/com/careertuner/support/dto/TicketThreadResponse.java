package com.careertuner.support.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 문의 단건의 전체 대화(원문 + 관리자 답변 + 사용자 추가 문의). */
public record TicketThreadResponse(
        Long id,
        String subject,
        String category,
        String status,
        LocalDateTime createdAt,
        List<TicketMessageView> messages
) {}
