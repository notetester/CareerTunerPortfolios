-- 개인 계정/IP/대화방 차단 관계를 물리 삭제하지 않고 해제 시각으로 보존한다.
-- 기존 UNIQUE 관계 키는 유지하며 재차단 INSERT가 같은 행의 deleted_at을 NULL로 복원한다.
-- information_schema guard를 사용해 재실행할 수 있다.

SET @ct_privacy_block_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_block'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_privacy_block_ddl := IF(
    @ct_privacy_block_column_exists = 0,
    'ALTER TABLE user_block ADD COLUMN deleted_at DATETIME NULL COMMENT ''차단 해제 시각. 재차단하면 NULL로 복원'' AFTER masked_label',
    'SELECT 1'
);
PREPARE ct_privacy_block_stmt FROM @ct_privacy_block_ddl;
EXECUTE ct_privacy_block_stmt;
DEALLOCATE PREPARE ct_privacy_block_stmt;

SET @ct_privacy_block_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_ip_block'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_privacy_block_ddl := IF(
    @ct_privacy_block_column_exists = 0,
    'ALTER TABLE user_ip_block ADD COLUMN deleted_at DATETIME NULL COMMENT ''IP 차단 해제 시각. 재등록하면 NULL로 복원'' AFTER label',
    'SELECT 1'
);
PREPARE ct_privacy_block_stmt FROM @ct_privacy_block_ddl;
EXECUTE ct_privacy_block_stmt;
DEALLOCATE PREPARE ct_privacy_block_stmt;

SET @ct_privacy_block_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'conversation_block'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_privacy_block_ddl := IF(
    @ct_privacy_block_column_exists = 0,
    'ALTER TABLE conversation_block ADD COLUMN deleted_at DATETIME NULL COMMENT ''대화방 차단 해제 시각. 재차단하면 NULL로 복원'' AFTER flags_json',
    'SELECT 1'
);
PREPARE ct_privacy_block_stmt FROM @ct_privacy_block_ddl;
EXECUTE ct_privacy_block_stmt;
DEALLOCATE PREPARE ct_privacy_block_stmt;

SET @ct_privacy_block_soft_delete_columns := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME IN ('user_block', 'user_ip_block', 'conversation_block')
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_privacy_block_verification_ok := IF(@ct_privacy_block_soft_delete_columns = 3, 1, 0);

DROP TEMPORARY TABLE IF EXISTS ct_privacy_block_guard;
CREATE TEMPORARY TABLE ct_privacy_block_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);
INSERT INTO ct_privacy_block_guard (guard_ok) VALUES (@ct_privacy_block_verification_ok);

SELECT @ct_privacy_block_soft_delete_columns AS soft_delete_columns,
       IF(@ct_privacy_block_verification_ok = 1, 'PASS', 'FAIL') AS result;

DROP TEMPORARY TABLE IF EXISTS ct_privacy_block_guard;
