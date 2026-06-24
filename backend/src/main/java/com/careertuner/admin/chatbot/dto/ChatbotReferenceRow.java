package com.careertuner.admin.chatbot.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 답한 대화 로그 1행(F3-A 참조 대화 표). chatbot_response_log + faq LEFT JOIN 결과.
 * MyBatis 매핑용 — record 대신 @Data(snake→camel 자동매핑).
 * <p>faqQuestion 은 matched_faq_id 로 join 한 FAQ 질문(없으면 null), handoff 는 전환 여부(0/1/NULL).
 */
@Data
public class ChatbotReferenceRow {
    private LocalDateTime createdAt;
    private String question;
    private String faqQuestion;
    private Double similarity;
    private Integer handoff;
}
