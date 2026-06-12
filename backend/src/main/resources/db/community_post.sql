ALTER TABLE community_post
      ADD COLUMN status         VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' AFTER difficulty,
      ADD COLUMN tags_json       JSON        NULL                         AFTER status,
      ADD COLUMN comment_count   INT         NOT NULL DEFAULT 0           AFTER view_count,
      ADD COLUMN like_count      INT         NOT NULL DEFAULT 0           AFTER comment_count,
      ADD COLUMN bookmark_count  INT         NOT NULL DEFAULT 0           AFTER like_count;
      
DROP INDEX idx_community_post_category ON community_post;
  CREATE INDEX idx_community_post_cat_status_created
      ON community_post (category, status, created_at DESC);
  CREATE INDEX idx_community_post_cat_like
      ON community_post (category, status, like_count DESC);