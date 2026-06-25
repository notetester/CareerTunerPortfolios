package com.careertuner.ai.chat;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * chatbot_conversation_memory 접근. LangChain4j ChatMemoryStore 가 사용.
 */
@Mapper
public interface ChatMemoryMapper {

    /** 메시지 JSON 조회 (없으면 null) */
    String findMessages(@Param("conversationId") Long conversationId);

    /** 메시지 JSON upsert */
    void upsert(@Param("conversationId") Long conversationId,
                @Param("messagesJson") String messagesJson);

    /** 대화 삭제 */
    void delete(@Param("conversationId") Long conversationId);

    /**
     * 새 대화 행 생성 → 생성된 conversation_id 를 holder["id"] 에 채운다.
     * holder["userId"] 가 있으면 소유자로 기록(없으면 NULL = 익명).
     */
    void createConversation(Map<String, Object> holder);

    /** 해당 유저의 가장 최근 대화 conversation_id (없으면 null). 복원 진입점. */
    Long findRecentConversationByUser(@Param("userId") Long userId);

    /** fork 로 만든 지원건 세션에 application_case_id·title 을 바인딩(세션↔지원건 매핑·목록 제목). */
    void bindCase(@Param("conversationId") Long conversationId,
                  @Param("applicationCaseId") Long applicationCaseId,
                  @Param("title") String title);

    /** 유저의 인테이크(지원건) 세션 목록 — application_case_id 있는 것만, 최근순 최대 5(사이드바용). */
    List<Map<String, Object>> findIntakeSessionsByUser(@Param("userId") Long userId);

    /** 대화 소유자 user_id(없으면 null). 메시지 조회 권한 확인용. */
    Long findOwnerUserId(@Param("conversationId") Long conversationId);
}
