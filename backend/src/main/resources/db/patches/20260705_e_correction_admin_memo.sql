-- E 첨삭 관리자 운영 메모. 공유 DB에는 팀 합의 후 적용한다.
SET @has_admin_memo := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'correction_request'
       AND COLUMN_NAME = 'admin_memo'
);

SET @ddl_admin_memo := IF(
    @has_admin_memo = 0,
    'ALTER TABLE correction_request ADD COLUMN admin_memo VARCHAR(2000) NULL AFTER ai_usage_log_id',
    'DO 0'
);

PREPARE stmt FROM @ddl_admin_memo;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
