package com.careertuner.admin.chatbot.dto;

import java.util.List;

/**
 * 참조 대화 표(F3-A) 페이지 응답. 디자인 푸터 "최근 N건 · 총 M건" + 페이저용.
 *
 * @param content 현재 페이지 행들(최신순)
 * @param total   조건(faq_referenced=1, 기간)에 맞는 전체 건수
 * @param page    0-base 페이지 번호
 * @param size    페이지 크기
 */
public record ChatbotReferencePage(
        List<ChatbotReferenceResponse> content,
        long total,
        int page,
        int size
) {
}
