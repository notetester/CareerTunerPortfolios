-- B residual fields for application deadlines and richer job/company analysis output.
-- Idempotent for existing development databases.

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE application_case ADD COLUMN deadline_date DATE NULL AFTER posting_date',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'application_case' AND column_name = 'deadline_date'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_analysis ADD COLUMN evidence JSON NULL AFTER summary',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'evidence'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_analysis ADD COLUMN ambiguous_conditions JSON NULL AFTER evidence',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'ambiguous_conditions'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE company_analysis ADD COLUMN verified_facts JSON NULL AFTER sources',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'verified_facts'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE company_analysis ADD COLUMN ai_inferences JSON NULL AFTER verified_facts',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'ai_inferences'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE company_analysis ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT ''JOB_POSTING'' AFTER ai_inferences',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'source_type'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE company_analysis ADD COLUMN checked_at DATETIME NULL AFTER source_type',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'checked_at'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE company_analysis ADD COLUMN refresh_recommended_at DATETIME NULL AFTER checked_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'refresh_recommended_at'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
