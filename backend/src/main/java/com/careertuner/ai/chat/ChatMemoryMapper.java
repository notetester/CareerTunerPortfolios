package com.careertuner.ai.chat;

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

    /** 새 대화 행 생성 → 생성된 conversation_id 를 holder["id"] 에 채운다 */
    void createConversation(Map<String, Object> holder);
}
