SET @add_payment_policy_snapshot = (
    SELECT IF(
      NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'payment'
           AND column_name = 'policy_snapshot_json'
      ),
      'ALTER TABLE payment ADD COLUMN policy_snapshot_json JSON NULL AFTER credit_amount',
      'SELECT 1'
    )
);
PREPARE stmt FROM @add_payment_policy_snapshot;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_subscription_policy_snapshot = (
    SELECT IF(
      NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'user_subscription'
           AND column_name = 'policy_snapshot_json'
      ),
      'ALTER TABLE user_subscription ADD COLUMN policy_snapshot_json JSON NULL AFTER current_period_end',
      'SELECT 1'
    )
);
PREPARE stmt FROM @add_subscription_policy_snapshot;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS billing_policy_change (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    target_type           VARCHAR(40) NOT NULL,
    target_code           VARCHAR(120) NOT NULL,
    current_snapshot_json JSON NULL,
    next_snapshot_json    JSON NOT NULL,
    effective_from        DATETIME NOT NULL,
    apply_mode            VARCHAR(40) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by            BIGINT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    canceled_by           BIGINT NULL,
    canceled_at           DATETIME NULL,
    applied_at            DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_billing_policy_change_target (target_type, target_code, status, effective_from),
    KEY idx_billing_policy_change_status (status, effective_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
