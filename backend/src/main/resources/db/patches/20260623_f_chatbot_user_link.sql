-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-23
--  대화 소유자(user_id) 연결 — 로그인 유저별 "이전 대화 자동 복원"의 토대.
--
--  배경:
--   chatbot_conversation_memory.conversation_id 는 익명 발급 번호라
--   "누구의 대화"인지 모른다. 유저별 복원을 위해 소유자 컬럼을 추가한다.
--   - user_id NULL  = 비로그인(익명) 세션 → 복원 대상 아님
--   - user_id 있음  = 해당 유저의 대화 → 최근 대화 1건을 복원
--   조회 패턴: WHERE user_id = ? ORDER BY updated_at DESC LIMIT 1
--   → 복합 인덱스 (user_id, updated_at) 로 단일 인덱스 탐색.
--
--  ⚠️ 선행조건: 이 패치 적용 후에야 백엔드가 정상 동작한다
--     (createConversation 이 user_id 컬럼에 INSERT 하므로 미적용 시 ask 실패).
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 로컬 dev DB 는 먼저 적용해 검증.
--  ⚠️ MySQL 8 은 ADD COLUMN IF NOT EXISTS 미지원 → 재실행 시 "Duplicate column"
--     이면 이미 적용된 것이다(무시).
--  실행: mysql -h <host> -u <user> -p <db> < 20260623_f_chatbot_user_link.sql
--
--  ☐ 적용 이력: (미적용) 로컬 적용일 ____  / 운영 공유 DB(team1_db) 적용일 ____
-- =====================================================================

ALTER TABLE chatbot_conversation_memory
    ADD COLUMN user_id BIGINT NULL COMMENT '대화 소유자(로그인 유저 id). NULL=익명 세션(복원 대상 아님)' AFTER conversation_id,
    ADD INDEX idx_ccm_user_updated (user_id, updated_at);

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE chatbot_conversation_memory;
-- SELECT conversation_id, user_id, updated_at FROM chatbot_conversation_memory ORDER BY updated_at DESC LIMIT 5;
