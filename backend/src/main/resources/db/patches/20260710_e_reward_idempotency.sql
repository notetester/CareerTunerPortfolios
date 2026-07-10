-- 동일 결제/지원 건/게시글/댓글 참조의 리워드가 재시도에서 중복 적립되지 않도록 멱등키를 추가한다.
-- 기존 중복 감사 이력은 삭제하지 않고, 참조별 최초 행에만 키를 채워 이후 중복 지급을 차단한다.

SET @has_reward_idempotency_key := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_reward_history'
       AND COLUMN_NAME = 'idempotency_key'
);

SET @ddl_reward_idempotency_key := IF(
    @has_reward_idempotency_key = 0,
    'ALTER TABLE user_reward_history ADD COLUMN idempotency_key VARCHAR(120) NULL COMMENT ''동일 참조 이벤트 중복 적립 방지 키'' AFTER event_code',
    'DO 0'
);

PREPARE stmt FROM @ddl_reward_idempotency_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE user_reward_history history
JOIN (
    SELECT MIN(id) AS keeper_id, user_id, event_code, ref_type, ref_id
      FROM user_reward_history
     WHERE ref_type IS NOT NULL
       AND ref_id IS NOT NULL
     GROUP BY user_id, event_code, ref_type, ref_id
) keeper ON keeper.keeper_id = history.id
SET history.idempotency_key = CONCAT(keeper.event_code, ':', keeper.ref_type, ':', keeper.ref_id)
WHERE history.idempotency_key IS NULL;

SET @has_reward_idempotency_index := (
    SELECT COUNT(*)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_reward_history'
       AND INDEX_NAME = 'uk_user_reward_history_idempotency'
);

SET @ddl_reward_idempotency_index := IF(
    @has_reward_idempotency_index = 0,
    'CREATE UNIQUE INDEX uk_user_reward_history_idempotency ON user_reward_history (user_id, idempotency_key)',
    'DO 0'
);

PREPARE stmt FROM @ddl_reward_idempotency_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
