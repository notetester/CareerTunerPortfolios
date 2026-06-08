-- B v1: 지원 건 보관/삭제, 공고문 revision, 분석 이력/확정/운영 메모 보강.
-- 기존 개발 DB에 반복 적용할 수 있도록 컬럼/인덱스 존재 여부를 확인한다.

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE application_case ADD COLUMN archived_at DATETIME NULL AFTER is_favorite',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'application_case' AND column_name = 'archived_at'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE application_case ADD COLUMN deleted_at DATETIME NULL AFTER archived_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'application_case' AND column_name = 'deleted_at'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_posting ADD COLUMN revision INT NOT NULL DEFAULT 1 AFTER application_case_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'job_posting' AND column_name = 'revision'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @rownum := 0;
UPDATE job_posting jp
JOIN (
    SELECT id, application_case_id,
           ROW_NUMBER() OVER (PARTITION BY application_case_id ORDER BY id) AS next_revision
    FROM job_posting
) ranked ON jp.id = ranked.id
SET jp.revision = ranked.next_revision;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_analysis ADD COLUMN job_posting_id BIGINT NULL AFTER application_case_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'job_posting_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_analysis ADD COLUMN job_posting_revision INT NULL AFTER job_posting_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'job_posting_revision'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_analysis ADD COLUMN confirmed_at DATETIME NULL AFTER summary',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'confirmed_at'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_analysis ADD COLUMN admin_memo VARCHAR(2000) NULL AFTER confirmed_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'admin_memo'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE company_analysis ADD COLUMN job_posting_id BIGINT NULL AFTER application_case_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'job_posting_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE company_analysis ADD COLUMN job_posting_revision INT NULL AFTER job_posting_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'job_posting_revision'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE company_analysis ADD COLUMN confirmed_at DATETIME NULL AFTER sources',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'confirmed_at'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE company_analysis ADD COLUMN admin_memo VARCHAR(2000) NULL AFTER confirmed_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'admin_memo'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS application_case_status_history (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    changed_by_user_id  BIGINT NULL,
    previous_status     VARCHAR(20) NULL,
    new_status          VARCHAR(20) NOT NULL,
    memo                VARCHAR(1000) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_application_case_status_history_case (application_case_id),
    CONSTRAINT fk_application_case_status_history_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE,
    CONSTRAINT fk_application_case_status_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
