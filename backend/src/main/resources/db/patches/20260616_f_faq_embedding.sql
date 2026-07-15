-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-16
--  faq 테이블에 bge-m3 임베딩 벡터 저장용 JSON 컬럼 추가.
--
--  배경:
--   FAQ 기반 RAG 안내 챗봇 구현을 위해 각 FAQ의 임베딩 벡터를
--   MySQL JSON 컬럼에 저장한다. bge-m3 모델 출력은 1024차원 float 배열.
--   FAQ 규모가 수십 개이므로 벡터 DB 없이 앱에서 코사인 유사도를 계산한다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용.
--     IF NOT EXISTS 패턴으로 이미 컬럼이 있으면 스킵 — 재실행 안전.
--  실행: mysql -h <host> -u <user> -p <db> < 20260616_f_faq_embedding.sql
--
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db @ 54.116.80.214) 적용 후 날짜 기록할 것.
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

-- ── 컬럼 추가 (없을 때만) ────────────────────────────────────────────

-- embedding: bge-m3 임베딩 벡터 (1024차원 float 배열, JSON)
SELECT COUNT(*) INTO @col FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='faq' AND COLUMN_NAME='embedding';
SET @s = IF(@col>0, 'SELECT 1',
  "ALTER TABLE faq ADD COLUMN embedding JSON NULL COMMENT 'bge-m3 임베딩 벡터 (1024차원)' AFTER view_count");
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW COLUMNS FROM faq;
