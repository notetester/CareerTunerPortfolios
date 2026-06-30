package com.careertuner.ai.intake;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * chatbot_intake_slot 접근 — 인테이크 슬롯(caseId·mode·originalQuery) DB 영속.
 * 한 conversationId = 한 슬롯(1:1). 재시작/재방문 후 슬롯 복원의 토대(Phase C/D).
 */
@Mapper
public interface ChatbotIntakeSlotMapper {

    /** 슬롯 upsert(conversation_id PK). 지원 건(caseId) 확정 세션의 턴 종료 시 호출. */
    void upsert(@Param("conversationId") Long conversationId,
                @Param("userId") Long userId,
                @Param("applicationCaseId") Long applicationCaseId,
                @Param("mode") String mode,
                @Param("originalQuery") String originalQuery,
                @Param("fetchedCases") String fetchedCases);

    /** conversationId 의 슬롯 1건(없으면 null). 복원·세션 판정 진입점. status 포함. */
    Map<String, Object> findByConversation(@Param("conversationId") Long conversationId);

    /**
     * 슬롯 생명주기 status 전환(PENDING→READY 등). upsert 와 분리해 status 만 갱신한다
     * (upsert 의 ON DUPLICATE KEY UPDATE 는 status 를 건드리지 않아 기존 단계를 보존).
     */
    void markStatus(@Param("conversationId") Long conversationId,
                    @Param("status") String status);
}
