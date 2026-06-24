package com.careertuner.admin.chatbot.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.chatbot.dto.FaqBrief;
import com.careertuner.admin.chatbot.dto.UnansweredMeta;
import com.careertuner.admin.chatbot.dto.UnansweredRow;

/**
 * 답 못한 질문 운영 조회·상태변경 매퍼(관리자).
 * 군집화는 서비스(QuestionClusterer)에서 임베딩 코사인으로 수행 — 매퍼는 행 로드만.
 * 적재는 support/chatbot 의 UnansweredQuestionMapper 가 담당한다.
 */
@Mapper
public interface AdminUnansweredMapper {

    /** 상태별 미응답 질문 행(임베딩 포함) 전체 로드 — 군집화 입력. 최신순. */
    List<UnansweredRow> findRowsByStatus(@Param("status") String status);

    /** best FAQ 표시용 요약(id → 질문·카테고리). */
    List<FaqBrief> findFaqBriefByIds(@Param("ids") List<Long> ids);

    /** 대표 행의 현재 상태(군집 재구성 시 같은 상태 풀에서 묶기 위함). 없으면 null. */
    String findStatusById(@Param("id") Long id);

    /**
     * 군집 멤버 행들의 상태를 일괄 변경(토픽 단위 처리).
     * CONVERTED(2단계 종료 상태)는 건드리지 않는다.
     * @return 변경된 행 수
     */
    int updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") String status);

    /** 공백 질문 드릴: 대표 id 의 질문 원문 + 발생 대화 id. 없으면 null. */
    UnansweredMeta findMetaById(@Param("id") Long id);

    /** 발생 대화의 메모리 JSON(messages_json). 없거나 빈 대화면 null. */
    String findConversationMessages(@Param("conversationId") Long conversationId);
}
