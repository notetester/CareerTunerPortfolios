-- =====================================================================
--  F 담당(커뮤니티) 스키마 마이그레이션 — 2026-06-12
--  community_post 테이블에 누락된 컬럼 5개 + 인덱스 2개 추가.
--
--  배경:
--   schema.sql에 community_post DDL이 두 벌 존재(06-08 원본 / 06-09 수정).
--   팀장 서버 DB는 첫 번째(원본)로 생성되어 status, tags_json,
--   comment_count, like_count, bookmark_count 컬럼이 없다.
--   코드(Mapper, Service)는 이 컬럼들을 이미 사용 중이므로 ALTER 필수.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용.
--     IF NOT EXISTS 패턴으로 이미 컬럼이 있으면 스킵 — 재실행 안전.
--  실행: mysql -h <host> -u <user> -p <db> < 20260612_f_community_post_columns.sql
--
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db @ localhost) 적용 후 날짜 기록할 것.
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

-- ── 컬럼 추가 (없을 때만) ────────────────────────────────────────────

-- status: 게시글 상태 (PUBLISHED / HIDDEN / DELETED)
SELECT COUNT(*) INTO @col FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='community_post' AND COLUMN_NAME='status';
SET @s = IF(@col>0, 'SELECT 1',
  "ALTER TABLE community_post ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' AFTER difficulty");
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- tags_json: 태그 캐시 (정규화 테이블 post_tag가 원본)
SELECT COUNT(*) INTO @col FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='community_post' AND COLUMN_NAME='tags_json';
SET @s = IF(@col>0, 'SELECT 1',
  'ALTER TABLE community_post ADD COLUMN tags_json JSON NULL AFTER status');
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- comment_count: 댓글 수 비정규화
SELECT COUNT(*) INTO @col FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='community_post' AND COLUMN_NAME='comment_count';
SET @s = IF(@col>0, 'SELECT 1',
  'ALTER TABLE community_post ADD COLUMN comment_count INT NOT NULL DEFAULT 0 AFTER view_count');
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- like_count: 좋아요 수 비정규화
SELECT COUNT(*) INTO @col FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='community_post' AND COLUMN_NAME='like_count';
SET @s = IF(@col>0, 'SELECT 1',
  'ALTER TABLE community_post ADD COLUMN like_count INT NOT NULL DEFAULT 0 AFTER comment_count');
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- bookmark_count: 북마크 수 비정규화
SELECT COUNT(*) INTO @col FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='community_post' AND COLUMN_NAME='bookmark_count';
SET @s = IF(@col>0, 'SELECT 1',
  'ALTER TABLE community_post ADD COLUMN bookmark_count INT NOT NULL DEFAULT 0 AFTER like_count');
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- ── 인덱스 추가 (없을 때만) ──────────────────────────────────────────

-- 카테고리별 최신글 조회 (status 포함)
SELECT COUNT(*) INTO @idx FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='community_post' AND INDEX_NAME='idx_community_post_cat_status_created';
SET @s = IF(@idx>0, 'SELECT 1',
  'CREATE INDEX idx_community_post_cat_status_created ON community_post (category, status, created_at DESC)');
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- 인기글 조회 (좋아요 순)
SELECT COUNT(*) INTO @idx FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='community_post' AND INDEX_NAME='idx_community_post_cat_like';
SET @s = IF(@idx>0, 'SELECT 1',
  'CREATE INDEX idx_community_post_cat_like ON community_post (category, status, like_count DESC)');
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW COLUMNS FROM community_post;
-- SHOW INDEX FROM community_post;
