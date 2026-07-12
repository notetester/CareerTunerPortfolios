-- 공통 알림함의 레코드가 실제 수신 플랫폼(ALL/WEB/MOBILE/DESKTOP)을 보존하도록 확장한다.
-- 기존 행과 destination_platform이 null인 선행 배포 후보 행은 ALL로 호환한다.

SET @ct_notification_destination_column_exists := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notification'
       AND COLUMN_NAME = 'destination_platform'
);
SET @ct_notification_destination_ddl := IF(
    @ct_notification_destination_column_exists = 0,
    'ALTER TABLE notification ADD COLUMN destination_platform ENUM(''ALL'', ''MOBILE'', ''DESKTOP'', ''WEB'') NOT NULL DEFAULT ''ALL'' COMMENT ''알림 노출 플랫폼. ALL은 모든 플랫폼'' AFTER sender_relation',
    'SELECT 1'
);
PREPARE ct_notification_destination_stmt FROM @ct_notification_destination_ddl;
EXECUTE ct_notification_destination_stmt;
DEALLOCATE PREPARE ct_notification_destination_stmt;

-- 컬럼이 nullable VARCHAR로 먼저 배포된 환경도 정규화한 뒤 canonical enum으로 맞춘다.
UPDATE notification
   SET destination_platform = 'ALL'
 WHERE destination_platform IS NULL
    OR destination_platform NOT IN ('ALL', 'MOBILE', 'DESKTOP', 'WEB');

SET @ct_notification_destination_column_canonical := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notification'
       AND COLUMN_NAME = 'destination_platform'
       AND LOWER(COLUMN_TYPE) = 'enum(''all'',''mobile'',''desktop'',''web'')'
       AND IS_NULLABLE = 'NO'
       AND COLUMN_DEFAULT = 'ALL'
);
SET @ct_notification_destination_ddl := IF(
    @ct_notification_destination_column_canonical = 0,
    'ALTER TABLE notification MODIFY COLUMN destination_platform ENUM(''ALL'', ''MOBILE'', ''DESKTOP'', ''WEB'') NOT NULL DEFAULT ''ALL'' COMMENT ''알림 노출 플랫폼. ALL은 모든 플랫폼'' AFTER sender_relation',
    'SELECT 1'
);
PREPARE ct_notification_destination_stmt FROM @ct_notification_destination_ddl;
EXECUTE ct_notification_destination_stmt;
DEALLOCATE PREPARE ct_notification_destination_stmt;

SET @ct_notification_destination_index_exists := (
    SELECT COUNT(*)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notification'
       AND INDEX_NAME = 'idx_notification_user_platform_unread'
);
SET @ct_notification_destination_ddl := IF(
    @ct_notification_destination_index_exists = 0,
    'ALTER TABLE notification ADD INDEX idx_notification_user_platform_unread (user_id, destination_platform, is_read, created_at DESC)',
    'SELECT 1'
);
PREPARE ct_notification_destination_stmt FROM @ct_notification_destination_ddl;
EXECUTE ct_notification_destination_stmt;
DEALLOCATE PREPARE ct_notification_destination_stmt;

SET @ct_notification_destination_column_valid := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notification'
       AND COLUMN_NAME = 'destination_platform'
       AND LOWER(COLUMN_TYPE) = 'enum(''all'',''mobile'',''desktop'',''web'')'
       AND IS_NULLABLE = 'NO'
       AND COLUMN_DEFAULT = 'ALL'
);
SET @ct_notification_destination_index_valid := (
    SELECT IF(
        GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) =
            'user_id,destination_platform,is_read,created_at',
        1,
        0
    )
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notification'
       AND INDEX_NAME = 'idx_notification_user_platform_unread'
);
SET @ct_notification_destination_invalid_rows := (
    SELECT COUNT(*)
      FROM notification
     WHERE destination_platform IS NULL
        OR destination_platform NOT IN ('ALL', 'MOBILE', 'DESKTOP', 'WEB')
);
SET @ct_notification_destination_verification_ok := IF(
       @ct_notification_destination_column_valid = 1
   AND @ct_notification_destination_index_valid = 1
   AND @ct_notification_destination_invalid_rows = 0,
    1,
    0
);

DROP TEMPORARY TABLE IF EXISTS ct_notification_destination_guard;
CREATE TEMPORARY TABLE ct_notification_destination_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);
INSERT INTO ct_notification_destination_guard (guard_ok)
VALUES (@ct_notification_destination_verification_ok);

SELECT @ct_notification_destination_column_valid AS destination_column_valid,
       @ct_notification_destination_index_valid AS destination_index_valid,
       @ct_notification_destination_invalid_rows AS invalid_row_count,
       IF(@ct_notification_destination_verification_ok = 1, 'PASS', 'FAIL') AS result;

DROP TEMPORARY TABLE IF EXISTS ct_notification_destination_guard;
