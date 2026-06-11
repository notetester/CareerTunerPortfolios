package com.careertuner.interview.rag;

import jakarta.validation.constraints.NotBlank;

/** RAG 지식베이스 문서 추가 요청. */
public record AddKnowledgeRequest(
        @NotBlank String kind,      // RUBRIC/QUESTION_BANK/COMPANY/GENERAL
        String title,
        @NotBlank String content,
        String source) {
}
