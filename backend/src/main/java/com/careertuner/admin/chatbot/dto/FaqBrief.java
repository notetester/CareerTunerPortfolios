package com.careertuner.admin.chatbot.dto;

import lombok.Data;

/**
 * best FAQ 표시용 요약(id → 질문·카테고리).
 */
@Data
public class FaqBrief {
    private Long id;
    private String question;
    private String category;
}
