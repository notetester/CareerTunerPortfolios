-- =====================================================================
--  F 담당(챗봇 에이전트) 스키마 마이그레이션 — 2026-06-23
--  LangChain4j 대화 메모리(conversationId 단위) 영속화 테이블.
--
--  배경:
--   챗봇이 멀티턴 에이전트가 되면서 conversationId 단위로 메시지 윈도우를
--   MySQL 에 보관한다. LangChain4j ChatMemoryStore 가 이 테이블을 읽고 쓴다.
--   messages_json 은 ChatMessageSerializer.messagesToJson 결과(메시지 배열 JSON).
--   conversation_id 는 새 대화 시작 시 서버가 AUTO_INCREMENT 로 발급한다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. IF NOT EXISTS 로 재실행 안전.
--  실행: mysql -h <host> -u <user> -p <db> < 20260623_f_chatbot_memory.sql
--
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db @ localhost) 적용 후 날짜 기록할 것.
-- =====================================================================

CREATE TABLE IF NOT EXISTS chatbot_conversation_memory (
    conversation_id BIGINT   NOT NULL AUTO_INCREMENT COMMENT '대화 세션 ID (서버 발급)',
    messages_json   JSON     NOT NULL COMMENT 'LangChain4j 메시지 윈도우 JSON',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE chatbot_conversation_memory;
