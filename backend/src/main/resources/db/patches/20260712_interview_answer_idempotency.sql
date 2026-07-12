-- 네트워크 응답 유실/재전송 시 같은 질문 답변의 AI 평가와 원본 연결을 한 번만 수행한다.
-- client_submission_id가 없는 기존 클라이언트는 nullable unique의 MySQL 의미에 따라 계속 여러 답변을 저장할 수 있다.

SET @ct_answer_submission_id_exists := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_answer'
       AND COLUMN_NAME = 'client_submission_id'
);
SET @ct_answer_idempotency_ddl := IF(
    @ct_answer_submission_id_exists = 0,
    'ALTER TABLE interview_answer ADD COLUMN client_submission_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER question_id',
    'SELECT 1'
);
PREPARE ct_answer_idempotency_stmt FROM @ct_answer_idempotency_ddl;
EXECUTE ct_answer_idempotency_stmt;
DEALLOCATE PREPARE ct_answer_idempotency_stmt;

SET @ct_answer_submission_id_canonical := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_answer'
       AND COLUMN_NAME = 'client_submission_id'
       AND LOWER(COLUMN_TYPE) = 'char(36)'
       AND CHARACTER_SET_NAME = 'ascii'
       AND COLLATION_NAME = 'ascii_bin'
       AND IS_NULLABLE = 'YES'
);
SET @ct_answer_idempotency_ddl := IF(
    @ct_answer_submission_id_canonical = 0,
    'ALTER TABLE interview_answer MODIFY COLUMN client_submission_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER question_id',
    'SELECT 1'
);
PREPARE ct_answer_idempotency_stmt FROM @ct_answer_idempotency_ddl;
EXECUTE ct_answer_idempotency_stmt;
DEALLOCATE PREPARE ct_answer_idempotency_stmt;

SET @ct_answer_submission_status_exists := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_answer'
       AND COLUMN_NAME = 'submission_status'
);
SET @ct_answer_idempotency_ddl := IF(
    @ct_answer_submission_status_exists = 0,
    'ALTER TABLE interview_answer ADD COLUMN submission_status ENUM(''PENDING'', ''COMPLETED'', ''FAILED'') NOT NULL DEFAULT ''COMPLETED'' AFTER client_submission_id',
    'SELECT 1'
);
PREPARE ct_answer_idempotency_stmt FROM @ct_answer_idempotency_ddl;
EXECUTE ct_answer_idempotency_stmt;
DEALLOCATE PREPARE ct_answer_idempotency_stmt;

-- 중간 배포본이 VARCHAR/nullable 상태였더라도 기존 정상 답변은 완료 상태로 보존한다.
UPDATE interview_answer
   SET submission_status = 'COMPLETED'
 WHERE submission_status IS NULL
    OR submission_status NOT IN ('PENDING', 'COMPLETED', 'FAILED');

SET @ct_answer_submission_status_canonical := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_answer'
       AND COLUMN_NAME = 'submission_status'
       AND LOWER(COLUMN_TYPE) = 'enum(''pending'',''completed'',''failed'')'
       AND IS_NULLABLE = 'NO'
       AND COLUMN_DEFAULT = 'COMPLETED'
);
SET @ct_answer_idempotency_ddl := IF(
    @ct_answer_submission_status_canonical = 0,
    'ALTER TABLE interview_answer MODIFY COLUMN submission_status ENUM(''PENDING'', ''COMPLETED'', ''FAILED'') NOT NULL DEFAULT ''COMPLETED'' AFTER client_submission_id',
    'SELECT 1'
);
PREPARE ct_answer_idempotency_stmt FROM @ct_answer_idempotency_ddl;
EXECUTE ct_answer_idempotency_stmt;
DEALLOCATE PREPARE ct_answer_idempotency_stmt;

-- 서비스는 UUID를 소문자 canonical 형식으로 정규화한다. 중간 배포 데이터도 같은 기준으로 맞춘다.
UPDATE interview_answer
   SET client_submission_id = LOWER(client_submission_id)
 WHERE client_submission_id IS NOT NULL
   AND client_submission_id <> LOWER(client_submission_id);

SET @ct_answer_duplicate_keys := (
    SELECT COUNT(*)
      FROM (
            SELECT question_id, client_submission_id
              FROM interview_answer
             WHERE client_submission_id IS NOT NULL
             GROUP BY question_id, client_submission_id
            HAVING COUNT(*) > 1
      ) duplicated_submission
);
SET @ct_answer_idempotency_index_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_answer'
       AND INDEX_NAME = 'uk_interview_answer_question_submission'
);
SET @ct_answer_idempotency_ddl := IF(
       @ct_answer_idempotency_index_exists = 0
   AND @ct_answer_duplicate_keys = 0,
    'ALTER TABLE interview_answer ADD UNIQUE INDEX uk_interview_answer_question_submission (question_id, client_submission_id)',
    'SELECT 1'
);
PREPARE ct_answer_idempotency_stmt FROM @ct_answer_idempotency_ddl;
EXECUTE ct_answer_idempotency_stmt;
DEALLOCATE PREPARE ct_answer_idempotency_stmt;

SET @ct_answer_submission_id_valid := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_answer'
       AND COLUMN_NAME = 'client_submission_id'
       AND LOWER(COLUMN_TYPE) = 'char(36)'
       AND CHARACTER_SET_NAME = 'ascii'
       AND COLLATION_NAME = 'ascii_bin'
       AND IS_NULLABLE = 'YES'
);
SET @ct_answer_submission_status_valid := (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_answer'
       AND COLUMN_NAME = 'submission_status'
       AND LOWER(COLUMN_TYPE) = 'enum(''pending'',''completed'',''failed'')'
       AND IS_NULLABLE = 'NO'
       AND COLUMN_DEFAULT = 'COMPLETED'
);
SET @ct_answer_idempotency_index_valid := (
    SELECT IF(
           MIN(NON_UNIQUE) = 0
       AND GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'question_id,client_submission_id',
        1,
        0
    )
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_answer'
       AND INDEX_NAME = 'uk_interview_answer_question_submission'
);
SET @ct_answer_invalid_submission_ids := (
    SELECT COUNT(*)
      FROM interview_answer
     WHERE client_submission_id IS NOT NULL
       AND client_submission_id NOT REGEXP '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$'
);
SET @ct_answer_invalid_statuses := (
    SELECT COUNT(*)
      FROM interview_answer
     WHERE submission_status IS NULL
        OR submission_status NOT IN ('PENDING', 'COMPLETED', 'FAILED')
);
SET @ct_answer_idempotency_verification_ok := IF(
       @ct_answer_submission_id_valid = 1
   AND @ct_answer_submission_status_valid = 1
   AND @ct_answer_idempotency_index_valid = 1
   AND @ct_answer_duplicate_keys = 0
   AND @ct_answer_invalid_submission_ids = 0
   AND @ct_answer_invalid_statuses = 0,
    1,
    0
);

DROP TEMPORARY TABLE IF EXISTS ct_answer_idempotency_guard;
CREATE TEMPORARY TABLE ct_answer_idempotency_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);
INSERT INTO ct_answer_idempotency_guard (guard_ok)
VALUES (@ct_answer_idempotency_verification_ok);

SELECT @ct_answer_submission_id_valid AS submission_id_column_valid,
       @ct_answer_submission_status_valid AS submission_status_column_valid,
       @ct_answer_idempotency_index_valid AS unique_index_valid,
       @ct_answer_duplicate_keys AS duplicate_key_count,
       @ct_answer_invalid_submission_ids AS invalid_submission_id_count,
       @ct_answer_invalid_statuses AS invalid_status_count,
       IF(@ct_answer_idempotency_verification_ok = 1, 'PASS', 'FAIL') AS result;

DROP TEMPORARY TABLE IF EXISTS ct_answer_idempotency_guard;
