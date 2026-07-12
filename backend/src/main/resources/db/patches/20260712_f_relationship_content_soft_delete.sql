-- F 영역의 사용자 관계·알림·커뮤니티 연결 데이터를 물리 삭제 대신 해제 시각으로 보존한다.
-- 각 DDL은 information_schema로 보호해 재실행할 수 있다.

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'collaboration_friendship' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE collaboration_friendship ADD COLUMN deleted_at DATETIME NULL COMMENT ''친구 해제 시각. 재수락하면 NULL로 복원'' AFTER created_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'collaboration_conversation_permission' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE collaboration_conversation_permission ADD COLUMN deleted_at DATETIME NULL COMMENT ''권한 회수 시각. 재부여하면 NULL로 복원'' AFTER updated_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'collaboration_conversation_invite_allow' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE collaboration_conversation_invite_allow ADD COLUMN deleted_at DATETIME NULL COMMENT ''초대 허용 해제 시각. 재허용하면 NULL로 복원'' AFTER created_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'community_interview_review' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE community_interview_review ADD COLUMN deleted_at DATETIME NULL COMMENT ''면접 후기 확장 정보 삭제 시각'' AFTER created_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'post_reaction' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE post_reaction ADD COLUMN deleted_at DATETIME NULL COMMENT ''리액션 취소 시각. 재선택하면 NULL로 복원'' AFTER created_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'comment_reaction' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE comment_reaction ADD COLUMN deleted_at DATETIME NULL COMMENT ''리액션 취소 시각. 재선택하면 NULL로 복원'' AFTER created_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'community_post_tag' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE community_post_tag ADD COLUMN deleted_at DATETIME NULL COMMENT ''게시글 태그 연결 해제 시각'' AFTER created_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'notification' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE notification ADD COLUMN deleted_at DATETIME NULL COMMENT ''사용자가 알림을 지운 시각'' AFTER created_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'post_scrap' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE post_scrap ADD COLUMN deleted_at DATETIME NULL COMMENT ''스크랩 해제 시각'' AFTER scrapped_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'post_subscription' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE post_subscription ADD COLUMN deleted_at DATETIME NULL COMMENT ''게시글 구독 해제 시각. 재구독하면 NULL로 복원'' AFTER created_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

SET @ct_f_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'comment_subscription' AND column_name = 'deleted_at'),
    'SELECT 1', 'ALTER TABLE comment_subscription ADD COLUMN deleted_at DATETIME NULL COMMENT ''댓글 구독 해제 시각. 재구독하면 NULL로 복원'' AFTER created_at');
PREPARE ct_f_stmt FROM @ct_f_sql; EXECUTE ct_f_stmt; DEALLOCATE PREPARE ct_f_stmt;

-- 과거 탈퇴 계정의 비공개 관계와 개인 보관 데이터도 활성 조회에서 즉시 제외한다.
UPDATE collaboration_friendship friendship
JOIN users account ON account.id = friendship.user_id OR account.id = friendship.friend_user_id
   SET friendship.deleted_at = COALESCE(friendship.deleted_at, NOW())
 WHERE account.status = 'DELETED' OR account.deleted_at IS NOT NULL;

UPDATE notification item
JOIN users account ON account.id = item.user_id
   SET item.deleted_at = COALESCE(item.deleted_at, NOW())
 WHERE account.status = 'DELETED' OR account.deleted_at IS NOT NULL;

UPDATE collaboration_conversation_permission permission_row
JOIN users account ON account.id = permission_row.user_id
   SET permission_row.deleted_at = COALESCE(permission_row.deleted_at, NOW())
 WHERE account.status = 'DELETED' OR account.deleted_at IS NOT NULL;

UPDATE collaboration_conversation_invite_allow allow_row
JOIN users account ON account.id = allow_row.user_id
   SET allow_row.deleted_at = COALESCE(allow_row.deleted_at, NOW())
 WHERE account.status = 'DELETED' OR account.deleted_at IS NOT NULL;

UPDATE post_scrap scrap
JOIN users account ON account.id = scrap.user_id
   SET scrap.deleted_at = COALESCE(scrap.deleted_at, NOW())
 WHERE account.status = 'DELETED' OR account.deleted_at IS NOT NULL;

UPDATE post_subscription subscription_row
JOIN users account ON account.id = subscription_row.user_id
   SET subscription_row.deleted_at = COALESCE(subscription_row.deleted_at, NOW())
 WHERE account.status = 'DELETED' OR account.deleted_at IS NOT NULL;

UPDATE comment_subscription subscription_row
JOIN users account ON account.id = subscription_row.user_id
   SET subscription_row.deleted_at = COALESCE(subscription_row.deleted_at, NOW())
 WHERE account.status = 'DELETED' OR account.deleted_at IS NOT NULL;

UPDATE post_reaction reaction
JOIN users account ON account.id = reaction.user_id
   SET reaction.deleted_at = COALESCE(reaction.deleted_at, NOW())
 WHERE account.status = 'DELETED' OR account.deleted_at IS NOT NULL;

UPDATE comment_reaction reaction
JOIN users account ON account.id = reaction.user_id
   SET reaction.deleted_at = COALESCE(reaction.deleted_at, NOW())
 WHERE account.status = 'DELETED' OR account.deleted_at IS NOT NULL;

-- 이미 탈퇴한 계정의 반응을 제외한 활성 행 수로 표시 캐시를 대사한다.
UPDATE community_post post
   SET post.like_count = (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = post.id AND r.reaction_type = 'LIKE' AND r.deleted_at IS NULL),
       post.dislike_count = (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = post.id AND r.reaction_type = 'DISLIKE' AND r.deleted_at IS NULL),
       post.recommend_count = (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = post.id AND r.reaction_type = 'RECOMMEND' AND r.deleted_at IS NULL),
       post.disrecommend_count = (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = post.id AND r.reaction_type = 'DISRECOMMEND' AND r.deleted_at IS NULL),
       post.bookmark_count = (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = post.id AND r.reaction_type = 'BOOKMARK' AND r.deleted_at IS NULL),
       post.scrap_count = (SELECT COUNT(*) FROM post_scrap s WHERE s.post_id = post.id AND s.deleted_at IS NULL);

UPDATE community_comment comment_row
   SET comment_row.like_count = (SELECT COUNT(*) FROM comment_reaction r WHERE r.comment_id = comment_row.id AND r.reaction_type = 'LIKE' AND r.deleted_at IS NULL),
       comment_row.dislike_count = (SELECT COUNT(*) FROM comment_reaction r WHERE r.comment_id = comment_row.id AND r.reaction_type = 'DISLIKE' AND r.deleted_at IS NULL),
       comment_row.recommend_count = (SELECT COUNT(*) FROM comment_reaction r WHERE r.comment_id = comment_row.id AND r.reaction_type = 'RECOMMEND' AND r.deleted_at IS NULL),
       comment_row.disrecommend_count = (SELECT COUNT(*) FROM comment_reaction r WHERE r.comment_id = comment_row.id AND r.reaction_type = 'DISRECOMMEND' AND r.deleted_at IS NULL);
