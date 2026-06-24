-- application_case_extraction quality gate metadata.
-- OpenAI fallback is intentionally metadata-only here and remains disabled by default.

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND column_name = 'extraction_strategy'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN extraction_strategy VARCHAR(40) NULL AFTER error_message',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND column_name = 'quality_score'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN quality_score INT NULL AFTER extraction_strategy',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND column_name = 'quality_status'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN quality_status VARCHAR(30) NULL AFTER quality_score',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND constraint_name = 'chk_case_extraction_quality_status'
);

SET @ddl = IF(@constraint_exists = 0,
    'ALTER TABLE application_case_extraction ADD CONSTRAINT chk_case_extraction_quality_status CHECK (quality_status IS NULL OR quality_status IN (''PASS'', ''REVIEW_REQUIRED'', ''FAILED''))',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND column_name = 'quality_report_json'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN quality_report_json JSON NULL AFTER quality_status',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND column_name = 'model_versions_json'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN model_versions_json JSON NULL AFTER quality_report_json',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND column_name = 'fallback_eligible'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN fallback_eligible TINYINT(1) NOT NULL DEFAULT 0 AFTER model_versions_json',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND column_name = 'fallback_reason'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN fallback_reason VARCHAR(255) NULL AFTER fallback_eligible',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND column_name = 'reviewed_at'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN reviewed_at DATETIME NULL AFTER fallback_reason',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
