-- E 환불정책 버전, 고지 이력
-- 게시된 정책은 불변이며 DRAFT 행만 수정한다.

CREATE TABLE IF NOT EXISTS refund_policy (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    policy_code    VARCHAR(50)  NOT NULL DEFAULT 'REFUND_DEFAULT',
    version        INT          NOT NULL,
    title          VARCHAR(255) NOT NULL,
    summary        VARCHAR(500) NULL,
    content        MEDIUMTEXT   NOT NULL,
    rules_json     JSON         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    is_adverse     TINYINT(1)   NOT NULL DEFAULT 0,
    effective_at   DATETIME     NULL,
    published_at   DATETIME     NULL,
    notice_id      BIGINT       NULL,
    created_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    draft_slot     TINYINT GENERATED ALWAYS AS
        (CASE WHEN status = 'DRAFT' THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_policy_code_version (policy_code, version),
    UNIQUE KEY uk_refund_policy_single_draft (policy_code, draft_slot),
    KEY idx_refund_policy_current (policy_code, status, effective_at, version),
    KEY idx_refund_policy_notice (notice_id),
    CONSTRAINT fk_refund_policy_notice FOREIGN KEY (notice_id) REFERENCES notice (id) ON DELETE SET NULL,
    CONSTRAINT fk_refund_policy_admin FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_refund_policy_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS refund_policy_acknowledgement (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    refund_policy_id BIGINT      NOT NULL,
    trigger_type     VARCHAR(30) NOT NULL,
    action_key       VARCHAR(120) NOT NULL DEFAULT 'GLOBAL',
    acknowledged_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_policy_ack (user_id, refund_policy_id, trigger_type, action_key),
    KEY idx_refund_policy_ack_policy (refund_policy_id, trigger_type),
    CONSTRAINT fk_refund_policy_ack_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refund_policy_ack_policy FOREIGN KEY (refund_policy_id) REFERENCES refund_policy (id) ON DELETE CASCADE,
    CONSTRAINT chk_refund_policy_ack_trigger CHECK
        (trigger_type IN ('NOTICE', 'PAYMENT', 'CREDIT_USE', 'BENEFIT_USE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT IGNORE INTO refund_policy
    (policy_code, version, title, summary, content, rules_json, status, is_adverse,
     effective_at, published_at, created_by)
VALUES
    ('REFUND_DEFAULT', 1, '환불 정책',
     '전자상거래 관련 법령과 서비스 운영 기준에 따른 기본 환불 정책입니다.',
     '결제 후 7일 이내이며 유료 기능을 사용하지 않은 경우 전액 환불을 신청할 수 있습니다. 크레딧 또는 사용권을 사용한 결제 건과 중복 결제, 시스템 오류 등 예외 사유는 운영자가 결제 및 사용 이력을 확인한 뒤 처리합니다.',
     JSON_OBJECT(
         'legalBasis', 'E_COMMERCE_ACT',
         'withdrawalDays', 7,
         'unusedPolicy', 'FULL_REFUND',
         'usedPolicy', 'MANUAL_REVIEW',
         'exceptionCodes', JSON_ARRAY('DUPLICATE_PAYMENT', 'SYSTEM_ERROR', 'LEGAL_REQUIREMENT'),
         'noticeScopes', JSON_ARRAY('PAYMENT', 'CREDIT_USE', 'BENEFIT_USE')
     ),
     'PUBLISHED', 0, '2026-01-01 00:00:00', CURRENT_TIMESTAMP, NULL);
