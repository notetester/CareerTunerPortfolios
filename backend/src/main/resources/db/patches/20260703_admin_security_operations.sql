CREATE TABLE IF NOT EXISTS admin_security_block_rule (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    rule_type          VARCHAR(30)  NOT NULL COMMENT 'USER/EMAIL/EMAIL_DOMAIN/IP/CIDR/IP_RANGE/COUNTRY/ASN',
    rule_value         VARCHAR(160) NOT NULL COMMENT '차단 대상 값. IP, CIDR, 국가코드, 이메일 도메인 등',
    scope              VARCHAR(30)  NOT NULL DEFAULT 'GLOBAL' COMMENT 'GLOBAL/LOGIN/COMMUNITY/AI/SUPPORT',
    action_type        VARCHAR(30)  NOT NULL DEFAULT 'BLOCK' COMMENT 'BLOCK/REVIEW/CHALLENGE/ALLOWLIST',
    category           VARCHAR(40)  NOT NULL DEFAULT 'MANUAL' COMMENT 'SPAM/ABUSE/BRUTE_FORCE/GEO/VPN/SECURITY/MANUAL',
    reason             VARCHAR(1000) NULL,
    memo               VARCHAR(2000) NULL,
    active             TINYINT(1)   NOT NULL DEFAULT 1,
    waf_sync_enabled   TINYINT(1)   NOT NULL DEFAULT 0,
    waf_sync_status    VARCHAR(30)  NOT NULL DEFAULT 'SKIPPED',
    waf_rule_id        VARCHAR(120) NULL,
    last_synced_at     DATETIME     NULL,
    expires_at         DATETIME     NULL,
    created_by         BIGINT       NULL,
    updated_by         BIGINT       NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_security_block_rule_active (active, rule_type, rule_value),
    KEY idx_admin_security_block_rule_waf (waf_sync_enabled, waf_sync_status),
    KEY idx_admin_security_block_rule_expires (expires_at),
    CONSTRAINT fk_admin_security_block_rule_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_security_block_rule_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '관리자 보안 차단/검토 규칙';

CREATE TABLE IF NOT EXISTS admin_security_waf_sync_event (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    block_rule_id          BIGINT       NULL,
    provider_code          VARCHAR(60)  NOT NULL DEFAULT 'MANUAL_WAF',
    operation_type         VARCHAR(30)  NOT NULL COMMENT 'UPSERT/DELETE/HEALTH_CHECK',
    status                 VARCHAR(30)  NOT NULL DEFAULT 'QUEUED',
    request_payload_json   JSON         NULL,
    response_payload_json  JSON         NULL,
    error_message          VARCHAR(1000) NULL,
    requested_by           BIGINT       NULL,
    requested_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at           DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_admin_security_waf_event_rule (block_rule_id, requested_at),
    KEY idx_admin_security_waf_event_status (status, requested_at),
    CONSTRAINT fk_admin_security_waf_event_rule FOREIGN KEY (block_rule_id) REFERENCES admin_security_block_rule (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_security_waf_event_requested_by FOREIGN KEY (requested_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'WAF 동기화 요청 및 처리 이력';

CREATE TABLE IF NOT EXISTS admin_security_provider_config (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    provider_code      VARCHAR(60)  NOT NULL,
    display_name       VARCHAR(120) NOT NULL,
    provider_type      VARCHAR(30)  NOT NULL COMMENT 'WAF/RISK/EMAIL/CAPTCHA',
    mode               VARCHAR(30)  NOT NULL DEFAULT 'MOCK',
    enabled            TINYINT(1)   NOT NULL DEFAULT 0,
    endpoint_url       VARCHAR(500) NULL,
    config_json        JSON         NOT NULL,
    health_status      VARCHAR(30)  NOT NULL DEFAULT 'UNKNOWN',
    last_checked_at    DATETIME     NULL,
    updated_by         BIGINT       NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_security_provider_code (provider_code),
    KEY idx_admin_security_provider_type (provider_type, enabled),
    CONSTRAINT fk_admin_security_provider_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '보안/WAF/외부위험 판단 Provider 설정';

CREATE TABLE IF NOT EXISTS admin_security_provider_health_history (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    provider_config_id BIGINT       NOT NULL,
    provider_code      VARCHAR(60)  NOT NULL,
    provider_type      VARCHAR(30)  NOT NULL,
    check_source       VARCHAR(30)  NOT NULL DEFAULT 'MANUAL',
    status_before      VARCHAR(30)  NULL,
    status_after       VARCHAR(30)  NOT NULL,
    detail_message     VARCHAR(1000) NULL,
    actor_user_id      BIGINT       NULL,
    checked_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_security_provider_health_provider (provider_code, checked_at),
    KEY idx_admin_security_provider_health_status (status_after, checked_at),
    KEY idx_admin_security_provider_health_actor (actor_user_id, checked_at),
    CONSTRAINT fk_admin_security_provider_health_provider FOREIGN KEY (provider_config_id) REFERENCES admin_security_provider_config (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_security_provider_health_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Provider 수동/스케줄러 헬스체크 결과 이력';

CREATE TABLE IF NOT EXISTS admin_security_review (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    review_type       VARCHAR(40)  NOT NULL COMMENT 'LOGIN_RISK/EXTERNAL_RISK/SECURITY_RISK/GENERAL',
    subject_type      VARCHAR(40)  NOT NULL COMMENT 'USER/IP/EMAIL/POST/COMMENT/SESSION',
    subject_value     VARCHAR(180) NOT NULL,
    risk_score        INT          NOT NULL DEFAULT 0,
    risk_level        VARCHAR(20)  NOT NULL DEFAULT 'LOW',
    status            VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    decision_action   VARCHAR(30)  NULL COMMENT 'ALLOW/BLOCK/CHALLENGE/ESCALATE/DISMISS',
    reason            VARCHAR(1000) NULL,
    evidence_json     JSON         NOT NULL,
    created_by        BIGINT       NULL,
    assigned_to       BIGINT       NULL,
    decided_by        BIGINT       NULL,
    decided_at        DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_security_review_status (status, risk_level, created_at),
    KEY idx_admin_security_review_subject (subject_type, subject_value),
    CONSTRAINT fk_admin_security_review_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_security_review_assigned_to FOREIGN KEY (assigned_to) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_security_review_decided_by FOREIGN KEY (decided_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '로그인/외부/보안 위험 일반 검토 큐';

CREATE TABLE IF NOT EXISTS admin_security_appeal_policy (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    policy_code             VARCHAR(60)  NOT NULL DEFAULT 'SECURITY_APPEAL_DEFAULT',
    display_name            VARCHAR(120) NOT NULL,
    enabled                 TINYINT(1)   NOT NULL DEFAULT 1,
    allow_multiple_open     TINYINT(1)   NOT NULL DEFAULT 0,
    max_open_per_subject    INT          NOT NULL DEFAULT 1,
    submitter_daily_limit   INT          NOT NULL DEFAULT 3,
    token_ttl_hours         INT          NOT NULL DEFAULT 24,
    config_json             JSON         NOT NULL,
    updated_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_security_appeal_policy_code (policy_code),
    CONSTRAINT fk_admin_security_appeal_policy_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '보안 차단 이의제기 정책';

CREATE TABLE IF NOT EXISTS admin_security_appeal (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    public_request_id VARCHAR(60)  NOT NULL,
    subject_type      VARCHAR(40)  NOT NULL,
    subject_value     VARCHAR(180) NOT NULL,
    block_rule_id     BIGINT       NULL,
    submitter_email   VARCHAR(255) NOT NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    reason            VARCHAR(2000) NULL,
    decision_reason   VARCHAR(2000) NULL,
    reviewed_by       BIGINT       NULL,
    reviewed_at       DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_security_appeal_public_id (public_request_id),
    KEY idx_admin_security_appeal_status (status, created_at),
    KEY idx_admin_security_appeal_subject (subject_type, subject_value),
    CONSTRAINT fk_admin_security_appeal_rule FOREIGN KEY (block_rule_id) REFERENCES admin_security_block_rule (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_security_appeal_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '보안 차단 이의제기 운영 큐';

INSERT INTO admin_security_provider_config
    (provider_code, display_name, provider_type, mode, enabled, endpoint_url, config_json)
VALUES
    ('MOCK_WAF', 'Mock WAF', 'WAF', 'MOCK', 0, NULL,
     JSON_OBJECT('dryRun', true, 'supports', JSON_ARRAY('IP', 'CIDR', 'IP_RANGE', 'COUNTRY', 'ASN'))),
    ('INTERNAL_RISK', '내부 위험 판단', 'RISK', 'INTERNAL', 1, NULL,
     JSON_OBJECT('scoreThreshold', 70, 'reviewThreshold', 45)),
    ('EMAIL_APPEAL', '이의제기 이메일 검증', 'EMAIL', 'INTERNAL', 1, NULL,
     JSON_OBJECT('tokenTtlHours', 24))
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    provider_type = VALUES(provider_type),
    updated_at = CURRENT_TIMESTAMP;

INSERT IGNORE INTO admin_security_appeal_policy
    (policy_code, display_name, config_json)
VALUES
    ('SECURITY_APPEAL_DEFAULT', '기본 보안 이의제기 정책',
     JSON_OBJECT('blockedEmailDomains', JSON_ARRAY(), 'captchaRequired', false, 'autoCloseDays', 14));

INSERT IGNORE INTO admin_system_policy
    (policy_code, display_name, description, config_json, schedule_type, active)
VALUES
    ('SECURITY_BLOCK_RECONCILE', '보안 차단/WAF 동기화 점검',
     '활성 차단 규칙과 WAF 동기화 큐를 주기적으로 점검하는 운영 정책입니다.',
     JSON_OBJECT('staleWafMinutes', 30, 'expiredRuleCleanup', true),
     'HOURLY', 1);
