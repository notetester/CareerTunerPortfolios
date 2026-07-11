-- 자동 숨김 임계 미만의 toxic 게시글을 운영자가 한 번만 검토할 수 있도록 수동 결정 이력을 남긴다.

SET @has_review_action := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'post_ai_result'
       AND COLUMN_NAME = 'review_action'
);

SET @ddl_review_action := IF(
    @has_review_action = 0,
    'ALTER TABLE post_ai_result ADD COLUMN review_action VARCHAR(10) NULL COMMENT ''경계 검열 결과 수동 결정(HIDE/KEEP)'' AFTER attempt_count',
    'DO 0'
);

PREPARE stmt FROM @ddl_review_action;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_reviewed_by := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'post_ai_result'
       AND COLUMN_NAME = 'reviewed_by'
);

SET @ddl_reviewed_by := IF(
    @has_reviewed_by = 0,
    'ALTER TABLE post_ai_result ADD COLUMN reviewed_by BIGINT NULL COMMENT ''경계 검열 결과를 결정한 관리자 ID'' AFTER review_action',
    'DO 0'
);

PREPARE stmt FROM @ddl_reviewed_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_reviewed_at := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'post_ai_result'
       AND COLUMN_NAME = 'reviewed_at'
);

SET @ddl_reviewed_at := IF(
    @has_reviewed_at = 0,
    'ALTER TABLE post_ai_result ADD COLUMN reviewed_at DATETIME NULL COMMENT ''경계 검열 결과 수동 검토 시각'' AFTER review_action',
    'DO 0'
);

PREPARE stmt FROM @ddl_reviewed_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_review_index := (
    SELECT COUNT(*)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'post_ai_result'
       AND INDEX_NAME = 'idx_post_ai_result_review'
);

SET @ddl_review_index := IF(
    @has_review_index = 0,
    'CREATE INDEX idx_post_ai_result_review ON post_ai_result (task_type, status, review_action, completed_at)',
    'DO 0'
);

PREPARE stmt FROM @ddl_review_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
