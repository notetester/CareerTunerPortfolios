-- E 첨삭 클라이언트 재시도 시 동일 요청의 AI 호출과 과금이 반복되지 않도록 요청키를 저장한다.
-- 공유 DB에는 팀 합의 후 적용한다.

SET @has_request_key := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'correction_request'
       AND COLUMN_NAME = 'request_key'
);

SET @ddl_request_key := IF(
    @has_request_key = 0,
    'ALTER TABLE correction_request ADD COLUMN request_key VARCHAR(120) NULL AFTER user_id',
    'DO 0'
);

PREPARE stmt FROM @ddl_request_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_request_key_index := (
    SELECT COUNT(*)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'correction_request'
       AND INDEX_NAME = 'uk_correction_request_user_key'
);

SET @ddl_request_key_index := IF(
    @has_request_key_index = 0,
    'CREATE UNIQUE INDEX uk_correction_request_user_key ON correction_request (user_id, request_key)',
    'DO 0'
);

PREPARE stmt FROM @ddl_request_key_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
