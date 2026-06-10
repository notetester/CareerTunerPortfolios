-- Toss 결제 식별에 필요한 최소 컬럼과 중복 처리 방지용 유니크 인덱스.

SET @add_provider = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE payment ADD COLUMN provider VARCHAR(20) NULL AFTER user_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'payment'
      AND column_name = 'provider'
);
PREPARE stmt FROM @add_provider;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_code = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE payment ADD COLUMN product_code VARCHAR(50) NULL AFTER provider',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'payment'
      AND column_name = 'product_code'
);
PREPARE stmt FROM @add_product_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_order_id = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE payment ADD COLUMN order_id VARCHAR(100) NULL AFTER product_code',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'payment'
      AND column_name = 'order_id'
);
PREPARE stmt FROM @add_order_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_payment_key = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE payment ADD COLUMN payment_key VARCHAR(200) NULL AFTER order_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'payment'
      AND column_name = 'payment_key'
);
PREPARE stmt FROM @add_payment_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_order_index = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE UNIQUE INDEX uk_payment_order_id ON payment (order_id)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'payment'
      AND index_name = 'uk_payment_order_id'
);
PREPARE stmt FROM @add_order_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_payment_key_index = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE UNIQUE INDEX uk_payment_payment_key ON payment (payment_key)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'payment'
      AND index_name = 'uk_payment_payment_key'
);
PREPARE stmt FROM @add_payment_key_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
