package com.careertuner.admin.chatbot.dto;

import lombok.Data;

/**
 * 공백 질문 드릴용 메타(질문 원문 + 발생 대화 id).
 * 매퍼 결과 타입은 record 대신 @Data 클래스(생성자 매핑 함정 회피).
 */
@Data
public class UnansweredMeta {
    private String question;
    private Long conversationId;
}
