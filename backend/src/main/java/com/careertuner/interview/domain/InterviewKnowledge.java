package com.careertuner.interview.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RAG 지식베이스 원본 문서. 벡터는 Qdrant, 원본 텍스트는 본 테이블. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewKnowledge {

    private Long id;
    private String kind;       // RUBRIC/QUESTION_BANK/COMPANY/GENERAL
    private String title;
    private String content;
    private String source;
    private Boolean indexed;
    private LocalDateTime createdAt;
}
