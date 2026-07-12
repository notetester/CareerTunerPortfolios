-- 관리자 크레딧 조정 재시도 중복 반영 방지와 과거 feature_type 길이 드리프트 복구.
-- 모든 DDL은 information_schema 확인 후 실행하여 재실행할 수 있다.

SET @ct_credit_feature_type_ddl := (
    SELECT IF(
        EXISTS (
            SELECT 1
              FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'credit_transaction'
               AND COLUMN_NAME = 'feature_type'
               AND (CHARACTER_MAXIMUM_LENGTH <> 80 OR IS_NULLABLE <> 'YES')
        ),
        'ALTER TABLE credit_transaction MODIFY feature_type VARCHAR(80) NULL',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ct_credit_feature_type_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ct_credit_request_key_ddl := (
    SELECT CASE
        WHEN
        NOT EXISTS (
            SELECT 1
              FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'credit_transaction'
               AND COLUMN_NAME = 'request_key'
        ) THEN
            'ALTER TABLE credit_transaction ADD COLUMN request_key VARCHAR(120) NULL COMMENT ''클라이언트 재시도 중복 반영 방지 키'' AFTER reason'
        WHEN EXISTS (
            SELECT 1
              FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'credit_transaction'
               AND COLUMN_NAME = 'request_key'
               AND (
                   DATA_TYPE <> 'varchar'
                   OR CHARACTER_MAXIMUM_LENGTH <> 120
                   OR IS_NULLABLE <> 'YES'
               )
        ) THEN
            'ALTER TABLE credit_transaction MODIFY COLUMN request_key VARCHAR(120) NULL COMMENT ''클라이언트 재시도 중복 반영 방지 키'''
        ELSE 'SELECT 1'
    END
);
PREPARE stmt FROM @ct_credit_request_key_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ct_credit_request_key_index_ddl := (
    SELECT CASE
        WHEN EXISTS (
            SELECT 1
              FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'credit_transaction'
               AND INDEX_NAME = 'uq_credit_transaction_user_type_request'
             GROUP BY INDEX_NAME
            HAVING MIN(NON_UNIQUE) = 0
               AND GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                   = 'user_id,type,request_key'
        ) THEN 'SELECT 1'
        WHEN EXISTS (
            SELECT 1
              FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'credit_transaction'
               AND INDEX_NAME = 'uq_credit_transaction_user_type_request'
        ) THEN
            'ALTER TABLE credit_transaction DROP INDEX uq_credit_transaction_user_type_request, ADD UNIQUE INDEX uq_credit_transaction_user_type_request (user_id, type, request_key)'
        ELSE
            'CREATE UNIQUE INDEX uq_credit_transaction_user_type_request ON credit_transaction (user_id, type, request_key)'
    END
);
PREPARE stmt FROM @ct_credit_request_key_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
