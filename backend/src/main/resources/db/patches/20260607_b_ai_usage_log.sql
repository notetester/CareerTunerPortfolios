-- B 완성형 구현용 ai_usage_log 보강 컬럼.
-- 이미 생성된 개발 DB에는 schema.sql 변경이 자동 반영되지 않을 수 있으므로 1회 적용한다.
-- 일부 컬럼이나 인덱스가 이미 있어도 재실행할 수 있도록 존재 여부를 확인한다.

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE ai_usage_log ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''SUCCESS'' AFTER feature_type',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_usage_log'
      AND column_name = 'status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE ai_usage_log ADD COLUMN model VARCHAR(80) NULL AFTER status',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_usage_log'
      AND column_name = 'model'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE ai_usage_log ADD COLUMN input_tokens INT NULL AFTER model',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_usage_log'
      AND column_name = 'input_tokens'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE ai_usage_log ADD COLUMN output_tokens INT NULL AFTER input_tokens',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_usage_log'
      AND column_name = 'output_tokens'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE ai_usage_log ADD COLUMN error_message VARCHAR(1000) NULL AFTER credit_used',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_usage_log'
      AND column_name = 'error_message'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_ai_usage_feature ON ai_usage_log (feature_type)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_usage_log'
      AND index_name = 'idx_ai_usage_feature'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
