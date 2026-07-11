-- 한 사용자가 같은 소셜 provider를 중복 연결하지 못하도록 데이터와 제약을 정리한다.
-- 과거 중복은 가장 먼저 연결된 id 한 건을 유지하고 나머지를 제거한다.
-- 적용 중 OAuth 연결 쓰기를 중지한 유지보수 구간에서 실행한다.

DELETE duplicate_social
  FROM user_social duplicate_social
  JOIN (
      SELECT user_id, provider, MIN(id) AS keep_id
        FROM user_social
       GROUP BY user_id, provider
      HAVING COUNT(*) > 1
  ) duplicated
    ON duplicated.user_id = duplicate_social.user_id
   AND duplicated.provider = duplicate_social.provider
   AND duplicated.keep_id <> duplicate_social.id;

SET @ct_social_index_columns := (
    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_social'
       AND INDEX_NAME = 'uk_user_social_user_provider'
);
SET @ct_social_index_non_unique := (
    SELECT MAX(NON_UNIQUE)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_social'
       AND INDEX_NAME = 'uk_user_social_user_provider'
);
SET @ct_social_index_correct := IF(
    COALESCE(@ct_social_index_columns, '') = 'user_id,provider'
        AND COALESCE(@ct_social_index_non_unique, 1) = 0,
    1,
    0
);

SET @ct_social_index_ddl := IF(
    @ct_social_index_correct = 0 AND @ct_social_index_columns IS NOT NULL,
    'ALTER TABLE user_social DROP INDEX uk_user_social_user_provider',
    'SELECT 1'
);
PREPARE stmt FROM @ct_social_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ct_social_index_ddl := IF(
    @ct_social_index_correct = 1,
    'SELECT 1',
    'ALTER TABLE user_social ADD UNIQUE INDEX uk_user_social_user_provider (user_id, provider)'
);
PREPARE stmt FROM @ct_social_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT
    (SELECT COUNT(*)
       FROM (
           SELECT user_id, provider
             FROM user_social
            GROUP BY user_id, provider
           HAVING COUNT(*) > 1
       ) duplicate_sets) AS duplicate_set_count,
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'user_social'
        AND INDEX_NAME = 'uk_user_social_user_provider') AS index_columns,
    (SELECT MAX(NON_UNIQUE)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'user_social'
        AND INDEX_NAME = 'uk_user_social_user_provider') AS non_unique;
