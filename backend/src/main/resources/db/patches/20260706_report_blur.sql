-- 신고 누적 자동 블러: 게시글에 누적 신고 수 컬럼 추가. 임계(community.report.blur-threshold, 기본 3) 이상이면
-- 비작성자에게 블러 처리(프론트에서 클릭 시 해제). TripTogether report_count 블러 이식.
ALTER TABLE community_post
    ADD COLUMN report_count INT NOT NULL DEFAULT 0 COMMENT '누적 신고 수(임계 이상 시 자동 블러)' AFTER dislike_count;
