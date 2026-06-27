package com.careertuner.support.chatbot;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 챗봇 턴 응답 로그(chatbot_response_log) 적재 매퍼(수집 전용).
 * <p>답함/못함·유사도·인용 FAQ·전환을 매 응답마다 1행 best-effort 로 적재한다.
 * 미스만 모으는 {@link UnansweredQuestionMapper} 와 달리 답한 턴까지 전부 들어가
 * 운영 콘솔 메트릭의 "분모(전 턴)"를 만든다. 조회/집계는 admin/chatbot 매퍼가 담당한다.
 */
@Mapper
public interface ResponseLogMapper {

    void insert(@Param("conversationId") Long conversationId,
                @Param("userId") Long userId,
                @Param("question") String question,
                @Param("responsePath") String responsePath,
                @Param("faqReferenced") boolean faqReferenced,
                @Param("topSimilarity") Double topSimilarity,
                @Param("matchedFaqId") Long matchedFaqId,
                @Param("handoff") boolean handoff);
}
