-- =====================================================================
--  W2 커뮤니티 리액션 전면 확장 (2026-07-05)
--  - 리액션 축(axis) 분리: RECOMMEND_AXIS(추천/비추천 — 트렌드·인기글용),
--    PREFERENCE(좋아요/싫어요 — 개인화용), BOOKMARK(즐겨찾기 — 링크형)
--    같은 축에서 반대 리액션 클릭 시 교체, 같은 것 재클릭 시 취소(토글)
--    → UNIQUE 키를 (user, target, axis) 로 재구성
--  - 익명 리액션(is_anonymous): 타인 시점 목록(프로필 활동/반응자 목록)에서 제외,
--    본인 시점 목록에는 표시, 집계 카운트에는 포함
--  - post_scrap: 스냅샷 보존형 스크랩(원본 수정/삭제와 무관하게 열람 가능)
--  - post_subscription / comment_subscription: 글/댓글 구독(새 댓글/답글 알림)
-- =====================================================================

-- 1) 게시글 리액션 확장
ALTER TABLE post_reaction
    ADD COLUMN axis         VARCHAR(20) NOT NULL DEFAULT 'PREFERENCE' COMMENT '리액션 축(RECOMMEND_AXIS/PREFERENCE/BOOKMARK)' AFTER reaction_type,
    ADD COLUMN is_anonymous TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '익명 리액션 — 타인 시점 목록 제외, 집계 포함' AFTER axis;

UPDATE post_reaction
   SET axis = CASE reaction_type WHEN 'BOOKMARK' THEN 'BOOKMARK' ELSE 'PREFERENCE' END;

ALTER TABLE post_reaction
    DROP INDEX uk_post_reaction,
    ADD UNIQUE KEY uk_post_reaction_axis (user_id, post_id, axis);

-- 2) 댓글 리액션 확장 (댓글에는 BOOKMARK 없음 — 축은 RECOMMEND_AXIS/PREFERENCE 만 사용)
ALTER TABLE comment_reaction
    ADD COLUMN axis         VARCHAR(20) NOT NULL DEFAULT 'PREFERENCE' COMMENT '리액션 축(RECOMMEND_AXIS/PREFERENCE)' AFTER reaction_type,
    ADD COLUMN is_anonymous TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '익명 리액션 — 타인 시점 목록 제외, 집계 포함' AFTER axis;

ALTER TABLE comment_reaction
    DROP INDEX uk_comment_reaction,
    ADD UNIQUE KEY uk_comment_reaction_axis (user_id, comment_id, axis);

-- 3) 카운트 캐시 컬럼 확장 (기존 like_count/bookmark_count 와 동형)
ALTER TABLE community_post
    ADD COLUMN dislike_count      INT NOT NULL DEFAULT 0 COMMENT '싫어요 수(개인화용 축)' AFTER like_count,
    ADD COLUMN recommend_count    INT NOT NULL DEFAULT 0 COMMENT '추천 수(트렌드·인기글용 축)' AFTER dislike_count,
    ADD COLUMN disrecommend_count INT NOT NULL DEFAULT 0 COMMENT '비추천 수' AFTER recommend_count,
    ADD COLUMN scrap_count        INT NOT NULL DEFAULT 0 COMMENT '스크랩 수(post_scrap 집계 캐시)' AFTER bookmark_count;

ALTER TABLE community_comment
    ADD COLUMN dislike_count      INT NOT NULL DEFAULT 0 COMMENT '싫어요 수' AFTER like_count,
    ADD COLUMN recommend_count    INT NOT NULL DEFAULT 0 COMMENT '추천 수' AFTER dislike_count,
    ADD COLUMN disrecommend_count INT NOT NULL DEFAULT 0 COMMENT '비추천 수' AFTER recommend_count;

-- 4) 스크랩 — 즐겨찾기(BOOKMARK, 링크형·원본 삭제 시 소멸)와 분리된 스냅샷 보존형
CREATE TABLE IF NOT EXISTS post_scrap (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    post_id               BIGINT       NULL COMMENT '원본 글 링크. 원본이 하드삭제되면 NULL(스냅샷은 유지)',
    snapshot_title        VARCHAR(255) NOT NULL COMMENT '스크랩 시점 제목',
    snapshot_content      MEDIUMTEXT   NOT NULL COMMENT '스크랩 시점 본문',
    snapshot_author_label VARCHAR(100) NOT NULL COMMENT '스크랩 시점 작성자 표시명(익명 글이면 "익명")',
    snapshot_category     VARCHAR(30)  NOT NULL COMMENT '스크랩 시점 카테고리',
    is_anonymous          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '익명 스크랩 — 타인 시점 목록 제외, 집계 포함',
    scrapped_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_post_scrap_user (user_id, scrapped_at DESC),
    KEY idx_post_scrap_post (post_id),
    CONSTRAINT fk_post_scrap_user FOREIGN KEY (user_id) REFERENCES users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_post_scrap_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '게시글 스크랩(스냅샷 보존 — 원본 수정/삭제와 무관)';

-- 5) 글 구독 — 새 댓글 시 구독자에게 POST_WATCH_COMMENT 알림(작성자 아닌 사람도 구독 가능, 토글)
CREATE TABLE IF NOT EXISTS post_subscription (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    post_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_subscription (user_id, post_id),
    KEY idx_post_subscription_post (post_id),
    CONSTRAINT fk_post_subscription_user FOREIGN KEY (user_id) REFERENCES users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_post_subscription_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '게시글 구독(새 댓글 알림)';

-- 6) 댓글 구독 — 새 답글 시 구독자에게 COMMENT_WATCH_REPLY 알림
CREATE TABLE IF NOT EXISTS comment_subscription (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    comment_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_subscription (user_id, comment_id),
    KEY idx_comment_subscription_comment (comment_id),
    CONSTRAINT fk_comment_subscription_user    FOREIGN KEY (user_id)    REFERENCES users (id)             ON DELETE CASCADE,
    CONSTRAINT fk_comment_subscription_comment FOREIGN KEY (comment_id) REFERENCES community_comment (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '댓글 구독(새 답글 알림)';
