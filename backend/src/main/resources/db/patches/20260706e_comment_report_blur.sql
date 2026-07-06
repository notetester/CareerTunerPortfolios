-- 댓글 신고 누적 자동 블러(게시글 report_blur parity). community_comment 에 report_count 추가.
-- 임계(ai_moderation_setting.report_blur_threshold, 기본 3) 이상이면 비작성자에게 블러(프론트 클릭 시 해제).
-- MySQL8: ADD COLUMN IF NOT EXISTS 미지원이라 information_schema 가드로 재실행 안전 처리.
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema=DATABASE() AND table_name='community_comment' AND column_name='report_count');
SET @s := IF(@c=0,
    "ALTER TABLE community_comment ADD COLUMN report_count INT NOT NULL DEFAULT 0 COMMENT '누적 신고 수(임계 이상 시 자동 블러)' AFTER disrecommend_count",
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
