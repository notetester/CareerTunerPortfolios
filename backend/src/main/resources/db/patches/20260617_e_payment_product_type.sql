SET @add_payment_product_type = (
    SELECT IF(
      NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'payment'
           AND COLUMN_NAME = 'product_type'
      ),
      'ALTER TABLE payment ADD COLUMN product_type VARCHAR(30) NOT NULL DEFAULT ''CREDIT'' AFTER provider',
      'SELECT 1'
    )
);
PREPARE stmt FROM @add_payment_product_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
