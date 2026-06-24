package com.careertuner.support.chatbot;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 챗봇이 FAQ로 답 못한 질문 적재 매퍼(수집 전용).
 * 조회/집계는 admin/chatbot 의 AdminUnansweredMapper 가 담당한다.
 */
@Mapper
public interface UnansweredQuestionMapper {

    void insert(@Param("question") String question,
                @Param("questionNorm") String questionNorm,
                @Param("topSimilarity") Double topSimilarity,
                @Param("embedding") String embedding,
                @Param("bestFaqId") Long bestFaqId,
                @Param("userId") Long userId,
                @Param("conversationId") Long conversationId);
}
