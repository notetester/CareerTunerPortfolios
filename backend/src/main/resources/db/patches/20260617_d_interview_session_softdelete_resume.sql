-- =====================================================================
--  D 담당(가상 면접) 스키마 마이그레이션 — 2026-06-17
--  interview_session 에 soft delete(deleted_at) + 복습 추적(last_resumed_at) 컬럼 추가.
--
--  배경:
--   - deleted_at: 최근 면접 기록을 사용자가 삭제할 때 행을 지우지 않고 시각만 기록(soft delete).
--                 목록 조회는 deleted_at IS NULL 만 노출. user·application_case 와 동일한 컨벤션.
--   - last_resumed_at: "복원 = 복습"으로 보고, 기존 세션을 복원할 때마다 시각을 갱신.
--                 최근 기록을 COALESCE(last_resumed_at, created_at) 기준으로 정렬·표시.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 컬럼 추가라 아래 가드형 ALTER 로 처리한다(재실행 안전).
--  실행: mysql -h <host> -u <user> -p <db> < 20260617_d_interview_session_softdelete_resume.sql
--
--  ✅ 적용 이력: 2026-06-17 운영 team1_db(@localhost) 적용 완료
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

-- ── interview_session.deleted_at 추가 ───────────────────────────────
SET @has_deleted_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'interview_session'
      AND COLUMN_NAME = 'deleted_at'
);
SET @ddl_deleted_at := IF(@has_deleted_at = 0,
    'ALTER TABLE interview_session
        ADD COLUMN deleted_at DATETIME NULL AFTER created_at',       -- soft delete 시각
    'DO 0');
PREPARE stmt FROM @ddl_deleted_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── interview_session.last_resumed_at 추가 ──────────────────────────
SET @has_last_resumed_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'interview_session'
      AND COLUMN_NAME = 'last_resumed_at'
);
SET @ddl_last_resumed_at := IF(@has_last_resumed_at = 0,
    'ALTER TABLE interview_session
        ADD COLUMN last_resumed_at DATETIME NULL AFTER deleted_at',  -- 복원(복습) 마지막 시각
    'DO 0');
PREPARE stmt FROM @ddl_last_resumed_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW COLUMNS FROM interview_session LIKE 'deleted_at';
-- SHOW COLUMNS FROM interview_session LIKE 'last_resumed_at';
