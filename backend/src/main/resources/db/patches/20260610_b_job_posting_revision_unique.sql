-- B job_posting revision collision protection.
-- Reports duplicate revision groups and stops before adding the unique key if any exist.

SELECT application_case_id,
       revision,
       COUNT(*) AS duplicate_count,
       GROUP_CONCAT(id ORDER BY id) AS duplicate_ids
FROM job_posting
GROUP BY application_case_id, revision
HAVING COUNT(*) > 1;

DROP TEMPORARY TABLE IF EXISTS ct_job_posting_revision_duplicate_guard;

CREATE TEMPORARY TABLE ct_job_posting_revision_duplicate_guard (
    duplicate_group_count INT NOT NULL,
    CONSTRAINT chk_job_posting_revision_no_duplicates CHECK (duplicate_group_count = 0)
);

INSERT INTO ct_job_posting_revision_duplicate_guard (duplicate_group_count)
SELECT COUNT(*)
FROM (
    SELECT application_case_id, revision
    FROM job_posting
    GROUP BY application_case_id, revision
    HAVING COUNT(*) > 1
) duplicate_revisions;

DROP TEMPORARY TABLE IF EXISTS ct_job_posting_revision_duplicate_guard;

SET @key_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'job_posting'
      AND index_name = 'uk_job_posting_case_revision'
);

SET @ddl = IF(@key_exists = 0,
    'ALTER TABLE job_posting ADD UNIQUE KEY uk_job_posting_case_revision (application_case_id, revision)',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
