-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-25
--  인테이크 슬롯 생명주기 status 컬럼 추가 (방향 A).
--
--  배경:
--   chatbot_intake_slot 행이 case 확정 후 영구 잔존하는데 "완료" 표식이 없어
--   isPersistedIntakeSession 이 영원히 true → 라우터 우회 ③ 직행 → 두 버그:
--    - 버그1: 완료 후 FAQ 의도("돈 돌려주세요")가 인테이크에 삼켜짐.
--    - 버그2: 완료 후 "그만"이 이탈 핸들러를 못 만나 FALLBACK.
--   슬롯 생명주기를 status 로 명시 관리해(PENDING→READY) 라우터가 정상 작동하게 한다.
--
--   - status : 인테이크 세션 단계.
--       PENDING : 인테이크 진행 중(슬롯 미완). 재시작/재방문 시 복원 대상.
--       READY   : 슬롯 충족(run 을 프런트에 위임한 시점). 더는 sticky-인테이크 아님 → 라우터 정상.
--       DONE    : (예약) 명시적 완료 표식. 현재 흐름(4-b1)에선 미사용 — 백엔드가 run 완료를 못 봄.
--
--  ⚠️ MySQL 8 은 ADD COLUMN 에 IF NOT EXISTS 미지원 → 이 ALTER 는 한 번만 적용한다(재실행 시 1060 에러).
--  ⚠️ 기존 행(이미 존재하는 인테이크 세션)은 DEFAULT 로 PENDING 이 채워진다 — 다음 인테이크 턴에
--     ready 도달하면 코드가 READY 로 승급(점진 자가 치유). 신규 회귀 없음(판단 로직은 별도 단계에서 전환).
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 로컬 dev DB 는 먼저 적용해 검증.
--  실행: mysql -h <host> -u <user> -p <db> < 20260625_f_chatbot_intake_slot_status.sql
--
--  ☐ 적용 이력: (미적용) 로컬 적용일 ____  / 운영 공유 DB(team1_db) 적용일 ____
-- =====================================================================

ALTER TABLE chatbot_intake_slot
    ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'PENDING'
        COMMENT '인테이크 세션 단계: PENDING(진행·복원대상)/READY(슬롯충족·라우터정상)/DONE(예약)'
        AFTER mode;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE chatbot_intake_slot;
-- SELECT conversation_id, application_case_id, mode, status, updated_at
--   FROM chatbot_intake_slot ORDER BY updated_at DESC LIMIT 5;
