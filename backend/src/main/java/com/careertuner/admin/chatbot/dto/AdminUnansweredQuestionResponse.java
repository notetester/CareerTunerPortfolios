package com.careertuner.admin.chatbot.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 답 못한 질문 군집 1개(운영 패널 3단계-1). 의미 기반(임베딩 코사인) 클러스터.
 * <p>1단계의 정규화 정확매칭을 격상 — "환불 어떻게" / "환불 방법"이 한 군집으로 묶인다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUnansweredQuestionResponse {

    /** 대표 행 id(군집 내 대표 질문의 행). 상태변경·초안·전환 PATCH/POST 대상. */
    private Long id;
    /** 추천 카테고리(가장 가까웠던 FAQ의 카테고리; 없으면 null). */
    private String category;
    /** 대표 질문(군집에서 가장 흔한 표현). */
    private String question;
    /** 군집에 묶인 총 문의 수. */
    private long frequency;
    /** 군집 내 FAQ 최고 유사도(가장 가까웠던 값; 전부 NULL이면 null). */
    private Double topSimilarity;
    /** 가장 가까웠던 기존 FAQ 질문(없으면 null) — "왜 답하지 못했나" 근거. */
    private String bestFaqQuestion;
    /** 군집 상태(조회 필터와 동일). */
    private String status;
    /** 마지막 발생 시각. */
    private LocalDateTime lastSeen;
    /** 묶인 변형 질문 목록(표현별 건수, 빈도 desc). */
    private List<QuestionVariant> variants;
}
