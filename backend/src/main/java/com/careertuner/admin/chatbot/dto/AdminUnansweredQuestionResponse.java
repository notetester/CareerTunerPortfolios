package com.careertuner.admin.chatbot.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 답 못한 질문 목록 1행(= question_norm 정규화 정확매칭 그룹 1개).
 * <p>군집화 아님 — 정규화 키 GROUP BY 빈도 집계(임베딩 군집화는 3단계).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUnansweredQuestionResponse {

    /** 그룹 대표 행 id(최신 행). 상태 변경 PATCH 대상 식별용. */
    private Long id;
    /** 대표 원문(그룹 내 최신 질문). */
    private String question;
    /** 정규화 키(그룹 식별·디버그용). */
    private String questionNorm;
    /** 그룹 내 행 수(반복 빈도). */
    private long frequency;
    /** 그룹 내 FAQ 최고 유사도(가장 가까웠던 값; 전부 NULL이면 null). */
    private Double topSimilarity;
    /** 그룹 상태(조회 필터와 동일). */
    private String status;
    /** 마지막 발생 시각. */
    private LocalDateTime lastSeen;
}
