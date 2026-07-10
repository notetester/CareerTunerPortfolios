-- 기존 user_consent 이력에 문서 버전을 추가한다.
-- 현재 공개 문서가 v2026.07이므로 기존 이력은 해당 버전으로 보정한다.
SET @consent_version_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_consent'
      AND COLUMN_NAME = 'consent_version'
);
SET @consent_version_sql = IF(
    @consent_version_exists = 0,
    'ALTER TABLE user_consent ADD COLUMN consent_version VARCHAR(40) NOT NULL DEFAULT ''v2026.07'' AFTER consent_type',
    'SELECT 1'
);
PREPARE consent_version_stmt FROM @consent_version_sql;
EXECUTE consent_version_stmt;
DEALLOCATE PREPARE consent_version_stmt;

UPDATE user_consent
SET consent_version = 'v2026.07'
WHERE consent_version IS NULL OR consent_version = '';
