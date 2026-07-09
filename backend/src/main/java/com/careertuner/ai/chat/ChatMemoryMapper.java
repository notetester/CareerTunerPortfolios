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

    /** 이 대화에 바인딩된 지원 건 id(없으면 null). fork 가드 영속 권위 — boundCaseId 인메모리 증발 시 DB로 보강. */
    Long findApplicationCaseId(@Param("conversationId") Long conversationId);

    /** 이 대화가 온보딩을 "그만"으로 거부했는지(거부 시각, NULL=거부 안 함). 온보딩 재진입 차단 판정용. */
    java.time.LocalDateTime findOnboardingDeclinedAt(@Param("conversationId") Long conversationId);

    /** 이 대화의 온보딩 거부를 영속(현재 시각 기록). 재시작 후에도 재권유 안 함. */
    void markOnboardingDeclined(@Param("conversationId") Long conversationId);

    /** 온보딩 거부 해제(재시작 확인 "네" 응답 시) — declined_at 을 NULL 로 되돌려 재진입을 허용한다. */
    void clearOnboardingDeclined(@Param("conversationId") Long conversationId);
}
