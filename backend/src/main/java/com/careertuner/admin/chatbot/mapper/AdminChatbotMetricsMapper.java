package com.careertuner.admin.chatbot.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.chatbot.dto.ChatbotMetricPoint;
import com.careertuner.admin.chatbot.dto.UnansweredRow;

/**
 * AI 상담 운영 콘솔 메트릭 밴드 집계 매퍼(관리자, 3단계-2).
 * 기간은 [from, toExclusive) 반열린 구간(일 경계는 서비스가 atStartOfDay 로 환산).
 * 군집화는 서비스(QuestionClusterer)에서 — 매퍼는 행 로드/단순 집계만.
 */
@Mapper
public interface AdminChatbotMetricsMapper {

    /** 기간 내 상태별 미응답 질문 행(군집화 입력 — embedding 포함). 최신순. */
    List<UnansweredRow> findRowsByStatusAndRange(@Param("status") String status,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("toExclusive") LocalDateTime toExclusive);

    /** 일자별 DISTINCT question_norm 수(스파크라인 소스). 데이터 있는 날만 반환 → 서비스에서 빈 날 0 채움. */
    List<ChatbotMetricPoint> countDailyDistinctNorm(@Param("status") String status,
                                                    @Param("from") LocalDateTime from,
                                                    @Param("toExclusive") LocalDateTime toExclusive);
}
