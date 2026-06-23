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

UPDATE user_subscription us
JOIN subscription_plan sp ON sp.code = us.plan_code
   SET us.policy_snapshot_json = JSON_OBJECT(
       'plan', JSON_OBJECT(
           'code', sp.code,
           'name', sp.name,
           'monthlyPrice', sp.monthly_price,
           'yearlyPrice', sp.yearly_price,
           'description', sp.description,
           'active', IF(sp.active = 1, CAST('true' AS JSON), CAST('false' AS JSON)),
           'sortOrder', sp.sort_order
       ),
       'benefitPolicies', COALESCE((
           SELECT JSON_ARRAYAGG(JSON_OBJECT(
               'planCode', sbp.plan_code,
               'benefitCode', sbp.benefit_code,
               'benefitName', sbp.benefit_name,
               'benefitType', sbp.benefit_type,
               'quantity', sbp.quantity,
               'resetCycle', sbp.reset_cycle,
               'overagePolicy', sbp.overage_policy,
               'creditCost', sbp.credit_cost,
               'active', IF(sbp.active = 1, CAST('true' AS JSON), CAST('false' AS JSON)),
               'sortOrder', sbp.sort_order
           ))
             FROM subscription_benefit_policy sbp
            WHERE sbp.active = 1
              AND (sbp.plan_code = us.plan_code OR (us.plan_code <> 'FREE' AND sbp.plan_code = 'FREE'))
       ), JSON_ARRAY()),
       'featureBenefitPolicies', COALESCE((
           SELECT JSON_ARRAYAGG(JSON_OBJECT(
               'featureType', afp.feature_type,
               'benefitCode', afp.benefit_code,
               'chargeUnit', afp.charge_unit,
               'includedInTicket', IF(afp.included_in_ticket = 1, CAST('true' AS JSON), CAST('false' AS JSON)),
               'defaultCreditCost', afp.default_credit_cost,
               'active', IF(afp.active = 1, CAST('true' AS JSON), CAST('false' AS JSON))
           ))
             FROM ai_feature_benefit_policy afp
            WHERE afp.active = 1
       ), JSON_ARRAY())
   )
 WHERE us.status = 'ACTIVE'
   AND us.policy_snapshot_json IS NULL;

UPDATE payment p
JOIN subscription_plan sp ON sp.code = p.plan
   SET p.policy_snapshot_json = JSON_OBJECT(
       'plan', JSON_OBJECT(
           'code', sp.code,
           'name', sp.name,
           'monthlyPrice', sp.monthly_price,
           'yearlyPrice', sp.yearly_price,
           'description', sp.description,
           'active', IF(sp.active = 1, CAST('true' AS JSON), CAST('false' AS JSON)),
           'sortOrder', sp.sort_order
       ),
       'benefitPolicies', COALESCE((
           SELECT JSON_ARRAYAGG(JSON_OBJECT(
               'planCode', sbp.plan_code,
               'benefitCode', sbp.benefit_code,
               'benefitName', sbp.benefit_name,
               'benefitType', sbp.benefit_type,
               'quantity', sbp.quantity,
               'resetCycle', sbp.reset_cycle,
               'overagePolicy', sbp.overage_policy,
               'creditCost', sbp.credit_cost,
               'active', IF(sbp.active = 1, CAST('true' AS JSON), CAST('false' AS JSON)),
               'sortOrder', sbp.sort_order
           ))
             FROM subscription_benefit_policy sbp
            WHERE sbp.active = 1
              AND (sbp.plan_code = p.plan OR (p.plan <> 'FREE' AND sbp.plan_code = 'FREE'))
       ), JSON_ARRAY()),
       'featureBenefitPolicies', COALESCE((
           SELECT JSON_ARRAYAGG(JSON_OBJECT(
               'featureType', afp.feature_type,
               'benefitCode', afp.benefit_code,
               'chargeUnit', afp.charge_unit,
               'includedInTicket', IF(afp.included_in_ticket = 1, CAST('true' AS JSON), CAST('false' AS JSON)),
               'defaultCreditCost', afp.default_credit_cost,
               'active', IF(afp.active = 1, CAST('true' AS JSON), CAST('false' AS JSON))
           ))
             FROM ai_feature_benefit_policy afp
            WHERE afp.active = 1
       ), JSON_ARRAY())
   )
 WHERE p.status = 'READY'
   AND p.product_type = 'SUBSCRIPTION'
   AND p.policy_snapshot_json IS NULL;

UPDATE payment p
JOIN credit_product cp ON cp.code = p.product_code
   SET p.policy_snapshot_json = JSON_OBJECT(
       'code', cp.code,
       'name', cp.name,
       'price', cp.price,
       'creditAmount', cp.credit_amount,
       'description', cp.description,
       'badge', cp.badge,
       'enabled', IF(cp.enabled = 1, CAST('true' AS JSON), CAST('false' AS JSON)),
       'sortOrder', cp.sort_order
   )
 WHERE p.status = 'READY'
   AND p.product_type = 'CREDIT'
   AND p.policy_snapshot_json IS NULL;
