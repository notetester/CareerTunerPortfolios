package com.careertuner.admin.chatbot.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.chatbot.dto.AdminUnansweredQuestionResponse;

/**
 * 답 못한 질문 운영 조회·상태변경 매퍼(관리자).
 * 적재는 support/chatbot 의 UnansweredQuestionMapper 가 담당한다.
 */
@Mapper
public interface AdminUnansweredMapper {

    /** question_norm 그룹 집계 목록(빈도 desc, 최신 desc). */
    List<AdminUnansweredQuestionResponse> findGrouped(@Param("status") String status,
                                                      @Param("limit") int limit,
                                                      @Param("offset") int offset);

    /** 대표 행의 원문 질문(초안 생성 컨텍스트용). 없으면 null. */
    String findQuestionById(@Param("id") Long id);

    /** 대표 id 가 속한 그룹의 행 수(반복 빈도). */
    long countByGroup(@Param("id") Long id);

    /**
     * 한 그룹(대표 id 가 속한 question_norm)의 상태를 일괄 변경.
     * CONVERTED(2단계 종료 상태)는 건드리지 않는다.
     * @return 변경된 행 수(0이면 대상 없음)
     */
    int updateStatusByGroup(@Param("id") Long id, @Param("status") String status);
}
