package com.careertuner.admin.chatbot.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.chatbot.dto.ChatbotReferenceRow;
import com.careertuner.admin.chatbot.dto.ResponseLogAggregate;
import com.careertuner.admin.chatbot.dto.ResponseLogDailyPoint;
import com.careertuner.admin.chatbot.dto.ThresholdHistogramRow;

/**
 * 챗봇 턴 응답 로그(chatbot_response_log) 집계 매퍼(관리자, 3단계-2 S2).
 * <p>기간은 [from, toExclusive) 반열린 구간(LocalDateTime). 읽기 전용 — 자동해결률·FAQ참조수·
 * 전환율(메트릭 3카드)과 참조 대화 표(F3-A)의 단일 소스.
 */
@Mapper
public interface AdminChatbotResponseLogMapper {

    /** 기간 집계: 전 턴 수/FAQ 근거 응답 수/전환 수(메트릭 분모·분자). */
    ResponseLogAggregate aggregate(@Param("from") LocalDateTime from,
                                   @Param("toExclusive") LocalDateTime toExclusive);

    /** 일자별 집계(스파크라인 소스). 데이터 있는 날만 반환 → 서비스에서 빈 날 0 채움. */
    List<ResponseLogDailyPoint> daily(@Param("from") LocalDateTime from,
                                      @Param("toExclusive") LocalDateTime toExclusive);

    /** 답한 대화(faq_referenced=1) 페이지 행 + faq 질문 join. 최신순. */
    List<ChatbotReferenceRow> findReferences(@Param("from") LocalDateTime from,
                                             @Param("toExclusive") LocalDateTime toExclusive,
                                             @Param("size") int size,
                                             @Param("offset") int offset);

    /** 답한 대화(faq_referenced=1) 전체 건수(페이지 total). */
    long countReferences(@Param("from") LocalDateTime from,
                         @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * 임계값 미리보기 분모: top_similarity 가 기록된 전 턴 수(F2). 날짜 무관(전체 분포 대상).
     */
    long countWithSimilarity();

    /**
     * 임계값 미리보기 분자: top_similarity &lt; threshold 인 턴 수(=이 임계라면 공백)(F2). 날짜 무관.
     */
    long countBelowThreshold(@Param("threshold") double threshold);

    /**
     * 임계값 미리보기 히스토그램 raw(0.05 폭 버킷별 턴 수)(F2). 데이터 있는 버킷만 반환 →
     * 서비스에서 전 구간(0.30~0.95) 빈 칸 0 채움. 날짜 무관(전체 분포 대상).
     */
    List<ThresholdHistogramRow> similarityHistogram();
}
