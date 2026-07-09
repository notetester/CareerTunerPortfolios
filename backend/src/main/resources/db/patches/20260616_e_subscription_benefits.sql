CREATE TABLE IF NOT EXISTS subscription_plan (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    code          VARCHAR(30) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    monthly_price INT NOT NULL DEFAULT 0,
    yearly_price  INT NULL,
    description   VARCHAR(500) NULL,
    active        TINYINT(1) NOT NULL DEFAULT 1,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_plan_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS subscription_benefit_policy (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    plan_code      VARCHAR(30) NOT NULL,
    benefit_code   VARCHAR(50) NOT NULL,
    benefit_name   VARCHAR(100) NOT NULL,
    benefit_type   VARCHAR(30) NOT NULL DEFAULT 'TICKET',
    quantity       INT NOT NULL DEFAULT 0,
    reset_cycle    VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    overage_policy VARCHAR(20) NOT NULL DEFAULT 'BLOCK',
    credit_cost    INT NOT NULL DEFAULT 0,
    active         TINYINT(1) NOT NULL DEFAULT 1,
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_benefit_policy (plan_code, benefit_code),
    KEY idx_subscription_benefit_policy_plan (plan_code),
    KEY idx_subscription_benefit_policy_benefit (benefit_code),
    CONSTRAINT fk_subscription_benefit_policy_plan FOREIGN KEY (plan_code) REFERENCES subscription_plan (code) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_subscription (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    user_id              BIGINT NOT NULL,
    plan_code            VARCHAR(30) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    current_period_start DATETIME NOT NULL,
    current_period_end   DATETIME NOT NULL,
    canceled_at          DATETIME NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_subscription_user_status (user_id, status),
    KEY idx_user_subscription_plan (plan_code),
    CONSTRAINT fk_user_subscription_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_subscription_plan FOREIGN KEY (plan_code) REFERENCES subscription_plan (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_benefit_balance (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    benefit_code       VARCHAR(50) NOT NULL,
    period_start       DATETIME NOT NULL,
    period_end         DATETIME NOT NULL,
    granted_quantity   INT NOT NULL DEFAULT 0,
    used_quantity      INT NOT NULL DEFAULT 0,
    remaining_quantity INT NOT NULL DEFAULT 0,
    source_plan_code   VARCHAR(30) NOT NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_benefit_balance_period (user_id, benefit_code, period_start),
    KEY idx_user_benefit_balance_user_period (user_id, period_start, period_end),
    KEY idx_user_benefit_balance_benefit (benefit_code),
    CONSTRAINT fk_user_benefit_balance_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_benefit_balance_plan FOREIGN KEY (source_plan_code) REFERENCES subscription_plan (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_feature_benefit_policy (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    feature_type        VARCHAR(50) NOT NULL,
    benefit_code        VARCHAR(50) NOT NULL,
    charge_unit         VARCHAR(30) NOT NULL,
    included_in_ticket  TINYINT(1) NOT NULL DEFAULT 1,
    default_credit_cost INT NOT NULL DEFAULT 0,
    active              TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_feature_benefit_policy_feature (feature_type),
    KEY idx_ai_feature_benefit_policy_benefit (benefit_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS benefit_transaction (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    benefit_code     VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount           INT NOT NULL,
    balance_after    INT NOT NULL,
    ref_type         VARCHAR(40) NULL,
    ref_id           BIGINT NULL,
    ai_usage_log_id  BIGINT NULL,
    reason           VARCHAR(255) NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_benefit_consume_ref (benefit_code, transaction_type, ref_type, ref_id),
    KEY idx_benefit_transaction_user (user_id),
    KEY idx_benefit_transaction_benefit (benefit_code),
    KEY idx_benefit_transaction_ai_usage (ai_usage_log_id),
    CONSTRAINT fk_benefit_transaction_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_benefit_transaction_ai_usage FOREIGN KEY (ai_usage_log_id) REFERENCES ai_usage_log (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT IGNORE INTO subscription_plan
    (code, name, monthly_price, yearly_price, description, active, sort_order)
VALUES
    ('FREE', '무료', 0, 0, '기본 체험 플랜', 1, 10),
    ('BASIC', '베이직', 9900, 7900, '가벼운 취업 준비 플랜', 1, 20),
    ('PRO', '프로', 29000, 23000, '실전 취업 준비 플랜', 1, 30),
    ('PREMIUM', '프리미엄', 49000, 39000, '고급 면접 패키지 플랜', 1, 40);

INSERT IGNORE INTO subscription_benefit_policy
    (plan_code, benefit_code, benefit_name, benefit_type, quantity, reset_cycle, overage_policy, credit_cost, active, sort_order)
VALUES
    ('FREE', 'APPLICATION_ANALYSIS', '지원건 분석권', 'TICKET', 3, 'MONTHLY', 'BLOCK', 0, 1, 10),
    ('FREE', 'MOCK_INTERVIEW', '모의면접권', 'TICKET', 1, 'MONTHLY', 'BLOCK', 0, 1, 20),
    ('FREE', 'VOICE_INTERVIEW', '음성면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 30),
    ('FREE', 'VIDEO_ANALYSIS', '영상분석권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 40),
    ('FREE', 'AVATAR_INTERVIEW', '아바타면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 50),
    ('BASIC', 'APPLICATION_ANALYSIS', '지원건 분석권', 'TICKET', 20, 'MONTHLY', 'BLOCK', 0, 1, 10),
    ('BASIC', 'MOCK_INTERVIEW', '모의면접권', 'TICKET', 10, 'MONTHLY', 'BLOCK', 0, 1, 20),
    ('BASIC', 'VOICE_INTERVIEW', '음성면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 30),
    ('BASIC', 'VIDEO_ANALYSIS', '영상분석권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 40),
    ('BASIC', 'AVATAR_INTERVIEW', '아바타면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 50),
    ('PRO', 'APPLICATION_ANALYSIS', '지원건 분석권', 'TICKET', 60, 'MONTHLY', 'BLOCK', 0, 1, 10),
    ('PRO', 'MOCK_INTERVIEW', '모의면접권', 'TICKET', 30, 'MONTHLY', 'BLOCK', 0, 1, 20),
    ('PRO', 'VOICE_INTERVIEW', '음성면접권', 'TICKET', 5, 'MONTHLY', 'BLOCK', 0, 1, 30),
    ('PRO', 'VIDEO_ANALYSIS', '영상분석권', 'TICKET', 1, 'MONTHLY', 'UPGRADE', 0, 1, 40),
    ('PRO', 'AVATAR_INTERVIEW', '아바타면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 50),
    ('PREMIUM', 'APPLICATION_ANALYSIS', '지원건 분석권', 'TICKET', 150, 'MONTHLY', 'BLOCK', 0, 1, 10),
    ('PREMIUM', 'MOCK_INTERVIEW', '모의면접권', 'TICKET', 60, 'MONTHLY', 'BLOCK', 0, 1, 20),
    ('PREMIUM', 'VOICE_INTERVIEW', '음성면접권', 'TICKET', 15, 'MONTHLY', 'BLOCK', 0, 1, 30),
    ('PREMIUM', 'VIDEO_ANALYSIS', '영상분석권', 'TICKET', 5, 'MONTHLY', 'BLOCK', 0, 1, 40),
    ('PREMIUM', 'AVATAR_INTERVIEW', '아바타면접권', 'TICKET', 5, 'MONTHLY', 'BLOCK', 0, 1, 50);

INSERT IGNORE INTO ai_feature_benefit_policy
    (feature_type, benefit_code, charge_unit, included_in_ticket, default_credit_cost, active)
VALUES
    ('JOB_POSTING_OCR', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 0, 1),
    ('JOB_ANALYSIS', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 0, 1),
    ('COMPANY_RESEARCH', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 0, 1),
    ('FIT_ANALYSIS', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 0, 1),
    ('INTERVIEW_QUESTION_GEN', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_FOLLOWUP_GEN', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_ANSWER_EVAL', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_CRITIC', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_REPORT', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_PLANNER', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_MODEL_ANSWER', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_VOICE_SESSION', 'VOICE_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_VIDEO_ANALYSIS', 'VIDEO_ANALYSIS', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_AVATAR_SESSION', 'AVATAR_INTERVIEW', 'PER_SESSION', 1, 0, 1);
