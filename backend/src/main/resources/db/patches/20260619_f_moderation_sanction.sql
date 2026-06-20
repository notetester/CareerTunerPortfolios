-- =====================================================================
--  F 담당(커뮤니티) 스키마 마이그레이션 — 2026-06-19
--  검열 누적 → 사용자 제재(자동 차단) 설정 컬럼 추가.
--  기존 ai_moderation_setting(엄격도·숨김 임계)에 제재 임계/차단기간을 더한다.
--
--  배경:
--   게시글 단위 자동숨김(hide_threshold, 신뢰도)과 별개로,
--   "부적절 글 누적" 사용자에 대한 사용자 단위 자동 제재 기준을 둔다.
--   - sanction_threshold: 숨김 처리된 글이 이 개수 이상이면 자동 차단(횟수, 신뢰도와 단위 다름)
--   - block_days: 자동 차단 시 차단 유지 일수(users.blocked_until = now + block_days)
--   위반 카운트는 community_post.status='HIDDEN' 집계 쿼리로 구하므로 users 테이블 변경은 없다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. ADD COLUMN IF NOT EXISTS 미지원(MySQL 8)이라
--     재실행 안전을 위해 information_schema 가드로 감싼다.
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db @ localhost) 적용 후 날짜 기록할 것.
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

-- sanction_threshold: 자동 차단을 트리거하는 누적 숨김 글 수 (기본 3)
SET @add_sanction := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE ai_moderation_setting ADD COLUMN sanction_threshold INT NOT NULL DEFAULT 3 AFTER hide_threshold',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_moderation_setting' AND column_name = 'sanction_threshold'
);
PREPARE stmt FROM @add_sanction; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- block_days: 자동 차단 유지 일수 (기본 7)
SET @add_block_days := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE ai_moderation_setting ADD COLUMN block_days INT NOT NULL DEFAULT 7 AFTER sanction_threshold',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_moderation_setting' AND column_name = 'block_days'
);
PREPARE stmt FROM @add_block_days; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW COLUMNS FROM ai_moderation_setting LIKE 'sanction_threshold';
-- SELECT id, strictness, hide_threshold, sanction_threshold, block_days FROM ai_moderation_setting;
