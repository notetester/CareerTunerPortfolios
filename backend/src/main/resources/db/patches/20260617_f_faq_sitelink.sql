-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-17
--  faq 테이블에 SiteLink(페이지 이동) 컬럼 2개 추가 + 주요 FAQ 링크 데이터 채우기.
--
--  배경:
--   RAG 챗봇 답변에 관련 페이지 이동 버튼을 제공하기 위해
--   FAQ마다 link_url(경로)과 link_label(버튼 텍스트)을 저장한다.
--   LLM이 링크를 생성하지 않고, 검색된 FAQ에 미리 등록된 링크를 그대로 내려보낸다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용.
--     IF NOT EXISTS 패턴으로 이미 컬럼이 있으면 스킵 — 재실행 안전.
--  실행: mysql -h <host> -u <user> -p <db> < 20260617_f_faq_sitelink.sql
--
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db @ localhost) 적용 후 날짜 기록할 것.
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

-- ── 컬럼 추가 (없을 때만) ────────────────────────────────────────────

-- link_url: 관련 페이지 경로 (내부 SPA 경로 또는 외부 URL)
SELECT COUNT(*) INTO @col FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='faq' AND COLUMN_NAME='link_url';
SET @s = IF(@col>0, 'SELECT 1',
  "ALTER TABLE faq ADD COLUMN link_url VARCHAR(200) NULL COMMENT '관련 페이지 경로' AFTER view_count");
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- link_label: 이동 버튼에 표시할 라벨
SELECT COUNT(*) INTO @col FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='faq' AND COLUMN_NAME='link_label';
SET @s = IF(@col>0, 'SELECT 1',
  "ALTER TABLE faq ADD COLUMN link_label VARCHAR(100) NULL COMMENT '이동 버튼 라벨' AFTER link_url");
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- ── 주요 FAQ 링크 데이터 ─────────────────────────────────────────────
-- 경로는 frontend/src/app/routes.ts 실제 라우트 기준으로 교정됨.
-- link_url이 NULL인 FAQ는 챗봇 응답에 이동 버튼이 표시되지 않는다.

UPDATE faq SET link_url = '/community',            link_label = '커뮤니티로 이동'       WHERE id = 32 AND link_url IS NULL;
UPDATE faq SET link_url = '/community',            link_label = '글쓰기 페이지로 이동'   WHERE id = 33 AND link_url IS NULL;
UPDATE faq SET link_url = '/community',            link_label = '면접후기 작성하기'      WHERE id = 34 AND link_url IS NULL;
UPDATE faq SET link_url = '/applications/new',     link_label = '지원 건 만들기'         WHERE id = 18 AND link_url IS NULL;
UPDATE faq SET link_url = '/dashboard',            link_label = '대시보드로 이동'        WHERE id = 16 AND link_url IS NULL;
UPDATE faq SET link_url = '/interview',            link_label = '면접 연습하기'          WHERE id = 13 AND link_url IS NULL;
UPDATE faq SET link_url = '/interview',            link_label = '면접 연습하기'          WHERE id = 26 AND link_url IS NULL;
UPDATE faq SET link_url = '/login',                link_label = '회원가입'               WHERE id = 4  AND link_url IS NULL;
UPDATE faq SET link_url = '/auth/forgot-password', link_label = '비밀번호 찾기'          WHERE id = 5  AND link_url IS NULL;
UPDATE faq SET link_url = '/settings',             link_label = '계정 설정'              WHERE id = 6  AND link_url IS NULL;
UPDATE faq SET link_url = '/pricing',              link_label = '플랜 관리'              WHERE id = 7  AND link_url IS NULL;
UPDATE faq SET link_url = '/billing',              link_label = '결제 내역'              WHERE id = 9  AND link_url IS NULL;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW COLUMNS FROM faq LIKE 'link%';
-- SELECT id, question, link_url, link_label FROM faq WHERE link_url IS NOT NULL;
