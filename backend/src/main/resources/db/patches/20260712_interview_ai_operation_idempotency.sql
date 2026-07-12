-- 질문 재생성/꼬리질문 생성의 응답 유실 재시도에서 모델 호출·사용량 기록·정산을 한 번만 수행한다.
-- reservation 행은 질문 변경과 같은 트랜잭션에 있으므로 커밋된 행은 곧 완료된 operation이다.

CREATE TABLE IF NOT EXISTS interview_ai_operation (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    feature_type  VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_id     BIGINT NOT NULL,
    operation_key VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_interview_ai_operation (user_id, feature_type, target_id, operation_key),
    KEY idx_interview_ai_operation_target (feature_type, target_id, created_at),
    CONSTRAINT fk_interview_ai_operation_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

SET @ct_interview_ai_operation_columns_valid := (
    SELECT COUNT(*) = 6
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_ai_operation'
       AND COLUMN_NAME IN ('id', 'user_id', 'feature_type', 'target_id', 'operation_key', 'created_at')
);
SET @ct_interview_ai_operation_unique_valid := (
    SELECT IF(
        MIN(NON_UNIQUE) = 0
        AND GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) =
            'user_id,feature_type,target_id,operation_key',
        1, 0)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_ai_operation'
       AND INDEX_NAME = 'uk_interview_ai_operation'
);
SET @ct_interview_ai_operation_target_index_valid := (
    SELECT IF(
        GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'feature_type,target_id,created_at',
        1, 0)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_ai_operation'
       AND INDEX_NAME = 'idx_interview_ai_operation_target'
);
SET @ct_interview_ai_operation_fk_valid := (
    SELECT COUNT(*) = 1
      FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_ai_operation'
       AND CONSTRAINT_NAME = 'fk_interview_ai_operation_user'
       AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @ct_interview_ai_operation_verification_ok := IF(
       @ct_interview_ai_operation_columns_valid = 1
   AND @ct_interview_ai_operation_unique_valid = 1
   AND @ct_interview_ai_operation_target_index_valid = 1
   AND @ct_interview_ai_operation_fk_valid = 1,
    1, 0
);

DROP TEMPORARY TABLE IF EXISTS ct_interview_ai_operation_guard;
CREATE TEMPORARY TABLE ct_interview_ai_operation_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);
INSERT INTO ct_interview_ai_operation_guard (guard_ok)
VALUES (@ct_interview_ai_operation_verification_ok);

SELECT @ct_interview_ai_operation_columns_valid AS columns_valid,
       @ct_interview_ai_operation_unique_valid AS unique_index_valid,
       @ct_interview_ai_operation_target_index_valid AS target_index_valid,
       @ct_interview_ai_operation_fk_valid AS foreign_key_valid,
       IF(@ct_interview_ai_operation_verification_ok = 1, 'PASS', 'FAIL') AS result;

DROP TEMPORARY TABLE IF EXISTS ct_interview_ai_operation_guard;
