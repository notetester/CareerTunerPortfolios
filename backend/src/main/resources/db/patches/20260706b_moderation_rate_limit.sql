-- =====================================================================
--  콘텐츠 중재 정책 콘솔 확장 — 2026-07-06
--  글/댓글/문의 작성 rate-limit(도배 방지) 3쌍 + 신고 누적 블러 임계를
--  ai_moderation_setting 단일행에 편입해 관리자가 런타임 편집하게 한다.
--  (TripTogether ContentModerationPolicy 의 rate-limit·reportThreshold 축 이식)
--
--  배경:
--   기존엔 글 rate-limit(community.post.rate-limit.*)과 신고 블러 임계
--   (community.report.blur-threshold)가 @Value 하드코딩이라 배포 없이는
--   조절 불가했다. 검열 설정(strictness/hide_threshold/sanction)과 같은
--   단일행에 담아 하나의 콘솔에서 관리한다. 댓글/문의 축은 신설.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. ADD COLUMN IF NOT EXISTS 미지원(MySQL 8)이라
--     재실행 안전을 위해 information_schema 가드로 감싼다.
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db @ localhost) 적용 후 날짜 기록할 것.
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

-- report_blur_threshold: 신고 누적 자동 블러 임계(이 수 이상 신고 시 비작성자 블러, 기본 3)
SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_moderation_setting ADD COLUMN report_blur_threshold INT NOT NULL DEFAULT 3 AFTER block_days',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_moderation_setting' AND column_name = 'report_blur_threshold');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

-- post_rate_window_seconds / post_rate_max: 게시글 작성 rate-limit(윈도 초 안에 max 건 이상이면 429, 0이면 비활성)
SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_moderation_setting ADD COLUMN post_rate_window_seconds INT NOT NULL DEFAULT 60 AFTER report_blur_threshold',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_moderation_setting' AND column_name = 'post_rate_window_seconds');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_moderation_setting ADD COLUMN post_rate_max INT NOT NULL DEFAULT 10 AFTER post_rate_window_seconds',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_moderation_setting' AND column_name = 'post_rate_max');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

-- comment_rate_window_seconds / comment_rate_max: 댓글 작성 rate-limit(신설, 기본 60초 20건)
SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_moderation_setting ADD COLUMN comment_rate_window_seconds INT NOT NULL DEFAULT 60 AFTER post_rate_max',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_moderation_setting' AND column_name = 'comment_rate_window_seconds');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_moderation_setting ADD COLUMN comment_rate_max INT NOT NULL DEFAULT 20 AFTER comment_rate_window_seconds',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_moderation_setting' AND column_name = 'comment_rate_max');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

-- inquiry_rate_window_seconds / inquiry_rate_max: 문의 작성 rate-limit 정책값(기본 600초 5건).
--   ※ 정책 저장·콘솔 노출까지만. 집행 배선은 support(문의) 도메인 소유자가 담당.
SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_moderation_setting ADD COLUMN inquiry_rate_window_seconds INT NOT NULL DEFAULT 600 AFTER comment_rate_max',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_moderation_setting' AND column_name = 'inquiry_rate_window_seconds');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_moderation_setting ADD COLUMN inquiry_rate_max INT NOT NULL DEFAULT 5 AFTER inquiry_rate_window_seconds',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_moderation_setting' AND column_name = 'inquiry_rate_max');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW COLUMNS FROM ai_moderation_setting;
-- SELECT id, report_blur_threshold, post_rate_window_seconds, post_rate_max,
--        comment_rate_window_seconds, comment_rate_max,
--        inquiry_rate_window_seconds, inquiry_rate_max FROM ai_moderation_setting;
