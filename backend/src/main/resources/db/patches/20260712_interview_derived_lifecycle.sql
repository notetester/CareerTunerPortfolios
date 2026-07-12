-- 모바일 답변 단위 음성/비언어 분석을 질문·답변에 연결한다.
-- 기존 세션 단위 분석 행은 question_id/answer_id NULL 상태로 그대로 호환한다.

SET @ct_media_question_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_media_analysis'
       AND COLUMN_NAME = 'question_id'
);
SET @ct_media_ddl := IF(
    @ct_media_question_column_exists = 0,
    'ALTER TABLE interview_media_analysis ADD COLUMN question_id BIGINT NULL AFTER interview_session_id',
    'SELECT 1'
);
PREPARE ct_media_stmt FROM @ct_media_ddl;
EXECUTE ct_media_stmt;
DEALLOCATE PREPARE ct_media_stmt;

SET @ct_media_answer_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_media_analysis'
       AND COLUMN_NAME = 'answer_id'
);
SET @ct_media_ddl := IF(
    @ct_media_answer_column_exists = 0,
    'ALTER TABLE interview_media_analysis ADD COLUMN answer_id BIGINT NULL AFTER question_id',
    'SELECT 1'
);
PREPARE ct_media_stmt FROM @ct_media_ddl;
EXECUTE ct_media_stmt;
DEALLOCATE PREPARE ct_media_stmt;

-- 중간 배포 또는 수동 데이터 중 이미 사라진 참조는 기존 세션 단위 행으로 안전하게 되돌린다.
UPDATE interview_media_analysis m
LEFT JOIN interview_answer a ON a.id = m.answer_id
   SET m.answer_id = NULL,
       m.question_id = NULL
 WHERE m.answer_id IS NOT NULL
   AND a.id IS NULL;

UPDATE interview_media_analysis m
LEFT JOIN interview_question q ON q.id = m.question_id
   SET m.question_id = NULL,
       m.answer_id = NULL
 WHERE m.question_id IS NOT NULL
   AND q.id IS NULL;

UPDATE interview_media_analysis m
JOIN interview_answer a ON a.id = m.answer_id
JOIN interview_question q ON q.id = m.question_id
   SET m.question_id = NULL,
       m.answer_id = NULL
 WHERE a.question_id <> m.question_id
    OR q.interview_session_id <> m.interview_session_id;

-- 같은 답변/분석 종류가 중복 저장된 중간 배포 데이터는 최신 행만 보존한다.
DELETE older
  FROM interview_media_analysis older
  JOIN interview_media_analysis newer
    ON newer.answer_id = older.answer_id
   AND newer.kind = older.kind
   AND newer.id > older.id
 WHERE older.answer_id IS NOT NULL;

SET @ct_media_question_index_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_media_analysis'
       AND INDEX_NAME = 'idx_media_analysis_question'
);
SET @ct_media_ddl := IF(
    @ct_media_question_index_exists = 0,
    'ALTER TABLE interview_media_analysis ADD INDEX idx_media_analysis_question (question_id)',
    'SELECT 1'
);
PREPARE ct_media_stmt FROM @ct_media_ddl;
EXECUTE ct_media_stmt;
DEALLOCATE PREPARE ct_media_stmt;

SET @ct_media_answer_unique_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_media_analysis'
       AND INDEX_NAME = 'uk_media_analysis_answer_kind'
);
SET @ct_media_ddl := IF(
    @ct_media_answer_unique_exists = 0,
    'ALTER TABLE interview_media_analysis ADD UNIQUE INDEX uk_media_analysis_answer_kind (answer_id, kind)',
    'SELECT 1'
);
PREPARE ct_media_stmt FROM @ct_media_ddl;
EXECUTE ct_media_stmt;
DEALLOCATE PREPARE ct_media_stmt;

SET @ct_media_question_fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_media_analysis'
       AND CONSTRAINT_NAME = 'fk_media_analysis_question'
       AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @ct_media_ddl := IF(
    @ct_media_question_fk_exists = 0,
    'ALTER TABLE interview_media_analysis ADD CONSTRAINT fk_media_analysis_question FOREIGN KEY (question_id) REFERENCES interview_question (id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE ct_media_stmt FROM @ct_media_ddl;
EXECUTE ct_media_stmt;
DEALLOCATE PREPARE ct_media_stmt;

SET @ct_media_answer_fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_media_analysis'
       AND CONSTRAINT_NAME = 'fk_media_analysis_answer'
       AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @ct_media_ddl := IF(
    @ct_media_answer_fk_exists = 0,
    'ALTER TABLE interview_media_analysis ADD CONSTRAINT fk_media_analysis_answer FOREIGN KEY (answer_id) REFERENCES interview_answer (id) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE ct_media_stmt FROM @ct_media_ddl;
EXECUTE ct_media_stmt;
DEALLOCATE PREPARE ct_media_stmt;

SET @ct_media_columns_valid := (
    SELECT COUNT(*) = 2 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_media_analysis'
       AND COLUMN_NAME IN ('question_id', 'answer_id')
       AND DATA_TYPE = 'bigint'
       AND IS_NULLABLE = 'YES'
);
SET @ct_media_unique_valid := (
    SELECT IF(
        MIN(NON_UNIQUE) = 0
        AND GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'answer_id,kind',
        1, 0)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_media_analysis'
       AND INDEX_NAME = 'uk_media_analysis_answer_kind'
);
SET @ct_media_fks_valid := (
    SELECT COUNT(*) = 2 FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_media_analysis'
       AND CONSTRAINT_NAME IN ('fk_media_analysis_question', 'fk_media_analysis_answer')
       AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @ct_media_invalid_links := (
    SELECT COUNT(*)
      FROM interview_media_analysis m
      LEFT JOIN interview_answer a ON a.id = m.answer_id
      LEFT JOIN interview_question q ON q.id = m.question_id
     WHERE (m.answer_id IS NOT NULL AND a.id IS NULL)
        OR (m.question_id IS NOT NULL AND q.id IS NULL)
        OR (m.answer_id IS NOT NULL AND m.question_id IS NULL)
        OR (m.answer_id IS NOT NULL AND a.question_id <> m.question_id)
        OR (m.question_id IS NOT NULL AND q.interview_session_id <> m.interview_session_id)
);
SET @ct_media_verification_ok := IF(
       @ct_media_columns_valid = 1
   AND @ct_media_unique_valid = 1
   AND @ct_media_fks_valid = 1
   AND @ct_media_invalid_links = 0,
   1, 0
);

DROP TEMPORARY TABLE IF EXISTS ct_media_lifecycle_guard;
CREATE TEMPORARY TABLE ct_media_lifecycle_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);
INSERT INTO ct_media_lifecycle_guard (guard_ok) VALUES (@ct_media_verification_ok);

SELECT @ct_media_columns_valid AS columns_valid,
       @ct_media_unique_valid AS answer_kind_unique_valid,
       @ct_media_fks_valid AS foreign_keys_valid,
       @ct_media_invalid_links AS invalid_link_count,
       IF(@ct_media_verification_ok = 1, 'PASS', 'FAIL') AS result;

DROP TEMPORARY TABLE IF EXISTS ct_media_lifecycle_guard;
