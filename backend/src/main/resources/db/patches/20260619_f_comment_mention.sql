-- =====================================================================
--  F 담당(커뮤니티) 스키마 마이그레이션 — 2026-06-19
--  대댓글 답글 멘션 — 피답글 대상을 불변 user_id 로 저장.
--
--  배경:
--   2단계 댓글에서 "대댓글의 답글"이 누구를 향한 것인지
--   구분되도록 멘션(@익명N)을 표시한다. 단 익명 번호는 글 단위 동적 계산이라
--   이름 텍스트를 본문에 박으면 삭제 등으로 번호가 밀릴 때 어긋난다.
--   → 멘션 대상을 변하지 않는 user_id 로 저장하고, 읽을 때 현재 익명번호로 렌더한다.
--   (이미지보드 quotelink/일반 멘션 베스트프랙티스: 표시명이 아니라 ID로 참조)
--
--  parent_id 는 그대로 최상위 댓글(루트 그룹)을 가리키고(삭제에 강건),
--  mention_user_id 는 답글이 가리키는 실제 대상 사용자다(없으면 NULL).
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. information_schema 가드로 재실행 안전.
--  ☐ 적용 이력: (미적용) 운영 공유 DB 적용 후 날짜 기록. 로컬/개인 DB 는 직접 실행.
-- =====================================================================

SET @add_mention := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE community_comment ADD COLUMN mention_user_id BIGINT NULL AFTER parent_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'community_comment' AND column_name = 'mention_user_id'
);
PREPARE stmt FROM @add_mention; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 검증 ─────────────────────────────────────────────────────────────
-- SHOW COLUMNS FROM community_comment LIKE 'mention_user_id';
