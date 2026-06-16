-- application_case_extraction active-job concurrency guard.
-- Existing development DBs do not pick up schema.sql changes automatically.

CREATE TABLE IF NOT EXISTS application_case_extraction (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    job_posting_id      BIGINT NULL,
    user_id             BIGINT NOT NULL,
    source_type         VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    active_status_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('QUEUED', 'RUNNING') THEN 1 ELSE NULL END
    ) STORED,
    error_message       VARCHAR(1000) NULL,
    started_at          DATETIME NULL,
    finished_at         DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_case_extraction_user_status (user_id, status, created_at, id),
    KEY idx_case_extraction_case_latest (application_case_id, created_at, id),
    KEY idx_case_extraction_job_posting (job_posting_id),
    UNIQUE KEY uk_case_extraction_active (application_case_id, active_status_marker),
    CONSTRAINT chk_case_extraction_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT fk_case_extraction_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE,
    CONSTRAINT fk_case_extraction_job_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id) ON DELETE SET NULL,
    CONSTRAINT fk_case_extraction_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

SELECT application_case_id,
       COUNT(*) AS active_count,
       GROUP_CONCAT(id ORDER BY id) AS active_extraction_ids
FROM application_case_extraction
WHERE status IN ('QUEUED', 'RUNNING')
GROUP BY application_case_id
HAVING COUNT(*) > 1;

DROP TEMPORARY TABLE IF EXISTS ct_case_extraction_active_duplicate_guard;

CREATE TEMPORARY TABLE ct_case_extraction_active_duplicate_guard (
    duplicate_case_count INT NOT NULL,
    CONSTRAINT chk_case_extraction_no_active_duplicates CHECK (duplicate_case_count = 0)
);

INSERT INTO ct_case_extraction_active_duplicate_guard (duplicate_case_count)
SELECT COUNT(*)
FROM (
    SELECT application_case_id
    FROM application_case_extraction
    WHERE status IN ('QUEUED', 'RUNNING')
    GROUP BY application_case_id
    HAVING COUNT(*) > 1
) duplicate_active_cases;

DROP TEMPORARY TABLE IF EXISTS ct_case_extraction_active_duplicate_guard;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND column_name = 'active_status_marker'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN active_status_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status IN (''QUEUED'', ''RUNNING'') THEN 1 ELSE NULL END) STORED AFTER status',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @key_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'application_case_extraction'
      AND index_name = 'uk_case_extraction_active'
);

SET @ddl = IF(@key_exists = 0,
    'ALTER TABLE application_case_extraction ADD UNIQUE KEY uk_case_extraction_active (application_case_id, active_status_marker)',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
