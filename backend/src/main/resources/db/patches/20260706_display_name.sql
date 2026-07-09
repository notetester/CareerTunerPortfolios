-- =====================================================================
--  표시명(display name) 통합 (2026-07-06)
--  커뮤니티 글/댓글에 "작성 시 선택한 표시용 닉네임 프로필"을 보관한다.
--  - 비익명 작성자 표시명을 user_nickname_profile 로 해석하기 위한 참조 컬럼.
--  - NULL 이면 계정 기본 프로필 → users.name 순으로 폴백(서비스에서 해석).
--  - 익명 작성은 이 컬럼을 쓰지 않는다(익명 마스킹 유지).
--  ※ 채팅(collaboration) 발신자 표시명은 기존 conversation_member_profile
--    (nickname_profile_id) + collaboration_conversation_member.anonymous/room_nickname
--    을 그대로 활용하므로 신규 컬럼이 없다(스키마 변경 없음).
--
--  멱등: 컬럼/인덱스/FK 존재 시 재실행 안전(정보스키마 가드).
-- =====================================================================

-- ── community_post.nickname_profile_id ──
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'community_post'
       AND column_name = 'nickname_profile_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE community_post ADD COLUMN nickname_profile_id BIGINT NULL COMMENT ''작성 시 선택한 표시용 닉네임 프로필(user_nickname_profile.id). NULL=계정 기본/계정명 표시'' AFTER is_anonymous',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'community_post'
       AND index_name = 'idx_community_post_nickname_profile'
);
SET @ddl := IF(@idx_exists = 0,
    'CREATE INDEX idx_community_post_nickname_profile ON community_post (nickname_profile_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE table_schema = DATABASE()
       AND table_name = 'community_post'
       AND constraint_name = 'fk_community_post_nickname_profile'
);
SET @ddl := IF(@fk_exists = 0,
    'ALTER TABLE community_post ADD CONSTRAINT fk_community_post_nickname_profile FOREIGN KEY (nickname_profile_id) REFERENCES user_nickname_profile (id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── community_comment.nickname_profile_id ──
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'community_comment'
       AND column_name = 'nickname_profile_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE community_comment ADD COLUMN nickname_profile_id BIGINT NULL COMMENT ''작성 시 선택한 표시용 닉네임 프로필(user_nickname_profile.id). NULL=계정 기본/계정명 표시'' AFTER is_anonymous',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'community_comment'
       AND index_name = 'idx_community_comment_nickname_profile'
);
SET @ddl := IF(@idx_exists = 0,
    'CREATE INDEX idx_community_comment_nickname_profile ON community_comment (nickname_profile_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE table_schema = DATABASE()
       AND table_name = 'community_comment'
       AND constraint_name = 'fk_community_comment_nickname_profile'
);
SET @ddl := IF(@fk_exists = 0,
    'ALTER TABLE community_comment ADD CONSTRAINT fk_community_comment_nickname_profile FOREIGN KEY (nickname_profile_id) REFERENCES user_nickname_profile (id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
