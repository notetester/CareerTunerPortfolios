-- D 면접 A/B/C 정본 컨텍스트 provenance 및 파생 데이터 soft-delete 보강.
-- 재실행 안전: 컬럼/인덱스는 information_schema 가드로 추가한다.

SET @ct_d_col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'source_snapshot'
);
SET @ct_d_ddl := IF(@ct_d_col_exists = 0,
    'ALTER TABLE interview_session ADD COLUMN source_snapshot JSON NULL AFTER report',
    'SELECT 1');
PREPARE ct_d_stmt FROM @ct_d_ddl;
EXECUTE ct_d_stmt;
DEALLOCATE PREPARE ct_d_stmt;

SET @ct_d_col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_question' AND COLUMN_NAME = 'deleted_at'
);
SET @ct_d_ddl := IF(@ct_d_col_exists = 0,
    'ALTER TABLE interview_question ADD COLUMN deleted_at DATETIME NULL AFTER sort_order',
    'SELECT 1');
PREPARE ct_d_stmt FROM @ct_d_ddl;
EXECUTE ct_d_stmt;
DEALLOCATE PREPARE ct_d_stmt;

SET @ct_d_index_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_question'
      AND INDEX_NAME = 'idx_interview_question_deleted'
);
SET @ct_d_ddl := IF(@ct_d_index_exists = 0,
    'ALTER TABLE interview_question ADD INDEX idx_interview_question_deleted (interview_session_id, deleted_at)',
    'SELECT 1');
PREPARE ct_d_stmt FROM @ct_d_ddl;
EXECUTE ct_d_stmt;
DEALLOCATE PREPARE ct_d_stmt;

SET @ct_d_col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_training_sample' AND COLUMN_NAME = 'deleted_at'
);
SET @ct_d_ddl := IF(@ct_d_col_exists = 0,
    'ALTER TABLE interview_training_sample ADD COLUMN deleted_at DATETIME NULL AFTER model',
    'SELECT 1');
PREPARE ct_d_stmt FROM @ct_d_ddl;
EXECUTE ct_d_stmt;
DEALLOCATE PREPARE ct_d_stmt;

SET @ct_d_index_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_training_sample'
      AND INDEX_NAME = 'idx_training_deleted'
);
SET @ct_d_ddl := IF(@ct_d_index_exists = 0,
    'ALTER TABLE interview_training_sample ADD INDEX idx_training_deleted (deleted_at, id)',
    'SELECT 1');
PREPARE ct_d_stmt FROM @ct_d_ddl;
EXECUTE ct_d_stmt;
DEALLOCATE PREPARE ct_d_stmt;

-- 기존 soft-delete 세션에 연결된 답변 원문 복제본도 즉시 학습/운영 대상에서 제외한다.
UPDATE interview_training_sample sample
JOIN interview_session session ON session.id = sample.interview_session_id
SET sample.deleted_at = COALESCE(sample.deleted_at, session.deleted_at)
WHERE session.deleted_at IS NOT NULL
  AND sample.deleted_at IS NULL;

SELECT
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'source_snapshot') AS session_snapshot_column,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_question' AND COLUMN_NAME = 'deleted_at') AS question_deleted_column,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_training_sample' AND COLUMN_NAME = 'deleted_at') AS training_deleted_column;
