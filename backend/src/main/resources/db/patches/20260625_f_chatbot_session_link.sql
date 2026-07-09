-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-25
--  지원건별 영속 세션(ChatGPT식) ①: 대화 세션 ↔ 지원 건 매핑 + 세션 제목.
--  ⚠️ 기존 테이블 ALTER — 적용 타이밍 주의(아래 CREATE 패치와 분리한 이유).
--
--  배경:
--   인테이크 챗봇이 confirmCase 시점에 새 conversationId 로 fork 되어
--   "지원 건 단위 세션"이 된다. 그 세션이 어떤 지원 건(application_case)에
--   속하는지와, 세션 목록에 보일 제목을 conversation 행에 직접 둔다.
--   - application_case_id NULL = 잡담/FAQ 대화(지원건 미연결). 인테이크 세션만 채워짐
--   - title            NULL = 미생성. 채우는 로직(caseId 기반 자동 생성)은 다음 Phase
--   세션 목록 조회(WHERE user_id=? ORDER BY updated_at DESC LIMIT 5)는
--   기존 idx_ccm_user_updated(user_id, updated_at) 로 충분하다. 여기서는
--   case 역참조(이 유저가 이 건으로 세션이 있나 — fork 중복 방지)용
--   idx_ccm_case 만 추가한다.
--
--  ⚠️ application_case_id 는 application_case(id) 로의 **논리 참조(FK 없음)**.
--     application_case 는 B파트 소유 + soft-delete(deleted_at/archived_at) 라
--     FK CASCADE/RESTRICT 의미가 어색하고, 기존 F 챗봇 패치 관례(response_log
--     .matched_faq_id, unanswered.best_faq_id 등)도 모두 논리참조다.
--     무결성은 애플리케이션이 책임진다.
--  ⚠️ 선행조건: 20260623_f_chatbot_user_link.sql 적용 후 실행(이 ALTER 가
--     user_id 뒤에 컬럼을 추가한다). 미적용이면 "AFTER user_id" 가 실패한다.
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 로컬 dev DB 는 먼저 적용해 검증.
--  ⚠️ MySQL 8 은 ADD COLUMN/INDEX IF NOT EXISTS 미지원 → 재실행 시
--     "Duplicate column/key name" 이면 이미 적용된 것이다(무시).
--  실행: mysql -h <host> -u <user> -p <db> < 20260625_f_chatbot_session_link.sql
--
--  ☐ 적용 이력: (미적용) 로컬 적용일 ____  / 운영 공유 DB(team1_db) 적용일 ____
-- =====================================================================

ALTER TABLE chatbot_conversation_memory
    ADD COLUMN application_case_id BIGINT NULL
        COMMENT '연결된 지원 건 id(application_case 논리 참조, FK 없음). NULL=잡담/FAQ 대화'
        AFTER user_id,
    ADD COLUMN title VARCHAR(255) NULL
        COMMENT '세션 제목(목록 표시용). NULL=미생성, 자동 생성은 다음 Phase'
        AFTER application_case_id,
    ADD INDEX idx_ccm_case (application_case_id);

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE chatbot_conversation_memory;
-- SELECT conversation_id, user_id, application_case_id, title, updated_at
--   FROM chatbot_conversation_memory ORDER BY updated_at DESC LIMIT 5;
