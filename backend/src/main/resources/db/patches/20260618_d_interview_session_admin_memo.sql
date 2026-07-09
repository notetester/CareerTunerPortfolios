-- =====================================================================
--  D 담당(가상 면접) 스키마 마이그레이션 — 2026-06-18
--  interview_session 에 관리자 운영 메모(admin_memo) 컬럼 추가.
--
--  배경:
--   - admin_memo: 관리자가 면접 세션을 모니터링하며 남기는 운영 메모(운영자 참고용).
--                 사용자에게 노출하지 않고 admin 세션 상세 화면에서만 조회/수정한다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 컬럼 추가라 아래 가드형 ALTER 로 처리한다(재실행 안전).
--  실행: mysql -h <host> -u <user> -p <db> < 20260618_d_interview_session_admin_memo.sql
--
--  ✅ 적용 이력: 2026-06-18 운영 공유 DB(team1_db @ localhost) 적용 완료
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

SET @has_admin_memo := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'interview_session'
      AND COLUMN_NAME = 'admin_memo'
);
SET @ddl_admin_memo := IF(@has_admin_memo = 0,
    'ALTER TABLE interview_session
        ADD COLUMN admin_memo TEXT NULL AFTER last_resumed_at',     -- 관리자 운영 메모
    'DO 0');
PREPARE stmt FROM @ddl_admin_memo;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW COLUMNS FROM interview_session LIKE 'admin_memo';
