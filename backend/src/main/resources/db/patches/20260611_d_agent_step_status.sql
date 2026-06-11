-- =====================================================================
--  D 담당(가상 면접) 스키마 마이그레이션 — 2026-06-11
--  자율 루프 trace 보강: interview_agent_step 에 status / elapsed_ms 컬럼 추가.
--
--  배경:
--   동적 오케스트레이터(자율 루프)가 각 단계의 성공/실패와 소요 시간을 기록한다.
--   프런트 AgentTimeline 이 "진행중→완료/실패" 상태 전이와 단계별 소요 시간을 표시한다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. CREATE TABLE IF NOT EXISTS 로는 안 들어가는
--     컬럼 추가라 아래 가드형 ALTER 로 처리한다(재실행/중복 적용 안전).
--  실행: mysql -h <host> -u <user> -p <db> < 20260611_d_agent_step_status.sql
-- =====================================================================

-- ── interview_agent_step.status 추가 ────────────────────────────────
SET @has_status := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'interview_agent_step'
      AND COLUMN_NAME = 'status'
);
SET @ddl_status := IF(@has_status = 0,
    'ALTER TABLE interview_agent_step
        ADD COLUMN status VARCHAR(12) NULL AFTER action',          -- DONE / FAILED
    'DO 0');
PREPARE stmt FROM @ddl_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── interview_agent_step.elapsed_ms 추가 ────────────────────────────
SET @has_elapsed := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'interview_agent_step'
      AND COLUMN_NAME = 'elapsed_ms'
);
SET @ddl_elapsed := IF(@has_elapsed = 0,
    'ALTER TABLE interview_agent_step
        ADD COLUMN elapsed_ms INT NULL AFTER detail',              -- 단계 소요 시간(ms)
    'DO 0');
PREPARE stmt FROM @ddl_elapsed;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW COLUMNS FROM interview_agent_step LIKE 'status';
-- SHOW COLUMNS FROM interview_agent_step LIKE 'elapsed_ms';
