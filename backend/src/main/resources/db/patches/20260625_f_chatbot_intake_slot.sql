-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-25
--  지원건별 영속 세션 ②: 인테이크 슬롯 상태 영속 테이블(신규).
--  신규 테이블이라 기존 데이터 영향 없음(위 ALTER 패치와 달리 타이밍 자유).
--
--  배경:
--   인테이크 진행 중 누적되는 슬롯(IntakeSlotTrace.SlotState: caseId/mode/
--   originalQuery/fetchedCases)을 인메모리 ConcurrentHashMap 에서 DB 로 옮겨,
--   서버 재시작 후에도 conversationId 로 복원한다. 한 세션(conversationId) =
--   한 슬롯 상태이므로 conversation_id 를 PK 로 두어 1:1 을 보장한다.
--   - application_case_id : 확정 지원 건(SlotState.caseId). 미확정 시 NULL
--   - mode               : 면접 모드(SlotState.mode)
--   - original_query     : 사용자 첫 발화(SlotState.originalQuery)
--   - fetched_cases      : 후보 지원건 목록 캐시(SlotState.fetchedCases).
--                          application_case 재조회로 대체 가능한 파생 캐시
--                          (영속은 선택 — 복원 충실성을 위해 컬럼만 마련)
--
--  ⚠️ conversation_id / application_case_id 는 각각 chatbot_conversation_memory /
--     application_case 로의 **논리 참조(FK 없음)** — 기존 F 챗봇 패치 관례.
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. IF NOT EXISTS 로 재실행 안전.
--     로컬 dev DB 는 먼저 적용해 검증.
--  실행: mysql -h <host> -u <user> -p <db> < 20260625_f_chatbot_intake_slot.sql
--
--  ☐ 적용 이력: (미적용) 로컬 적용일 ____  / 운영 공유 DB(team1_db) 적용일 ____
-- =====================================================================

CREATE TABLE IF NOT EXISTS chatbot_intake_slot (
    conversation_id     BIGINT        NOT NULL COMMENT '세션(대화) id — chatbot_conversation_memory 논리 참조(FK 없음), 1:1',
    user_id             BIGINT        NULL     COMMENT '슬롯 소유자(로그인 유저 id). 비로그인 NULL',
    application_case_id BIGINT        NULL     COMMENT '확정 지원 건 id(application_case 논리 참조, FK 없음). 미확정 NULL',
    mode                VARCHAR(20)   NULL     COMMENT '면접 모드: BASIC/JOB/PERSONALITY/PRESSURE/RESUME/COMPANY',
    original_query      VARCHAR(1000) NULL     COMMENT '사용자 첫 발화(verbatim)',
    fetched_cases       JSON          NULL     COMMENT '후보 지원건 목록 캐시(JSON). 재조회로 대체 가능한 파생 캐시',
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE chatbot_intake_slot;
-- SELECT conversation_id, user_id, application_case_id, mode, updated_at
--   FROM chatbot_intake_slot ORDER BY updated_at DESC LIMIT 5;
