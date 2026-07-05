-- 차단 집행 엔진: 규칙 우선순위/제어모드/배치 귀속/유효상태 + IP 정책 배치 + 차단 접근 로그.
-- TripTogether(config/BlockRuleCacheService·IpBlockInterceptor, IP_BLOCK_BATCH*, BLOCK_ACCESS_LOG)를
-- CareerTuner securityops 로 이식. 규칙 값은 기존 단일 rule_value 문자열을 rule_type 별로 해석한다.

-- 1) 차단 규칙 확장: 우선순위·제어모드·배치 귀속·유효상태
ALTER TABLE admin_security_block_rule
    ADD COLUMN priority            INT          NOT NULL DEFAULT 100 COMMENT '평가 우선순위(DESC). 높을수록 먼저 매칭' AFTER category,
    ADD COLUMN control_mode        VARCHAR(30)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/BATCH/MANUAL_OVERRIDE' AFTER priority,
    ADD COLUMN batch_id            BIGINT       NULL COMMENT '이 규칙이 속한 IP 정책 배치(admin_ip_block_batch.id)' AFTER control_mode,
    ADD COLUMN is_effective_active TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '규칙 활성·미만료·배치활성을 종합한 최종 유효상태(캐시 로드 기준)' AFTER active,
    ADD KEY idx_admin_security_block_rule_effective (is_effective_active, priority),
    ADD KEY idx_admin_security_block_rule_batch (batch_id);

-- 2) IP 정책 배치 — 정책기관 피드/대량 규칙을 묶어 그룹 제어
CREATE TABLE IF NOT EXISTS admin_ip_block_batch (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    batch_code         VARCHAR(80)  NOT NULL COMMENT '배치 식별 코드(예: MANUAL_FEED_20260706_1)',
    batch_name         VARCHAR(160) NOT NULL,
    source_type        VARCHAR(40)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/POLICY_FEED_CSV/POLICY_FEED_JSON/POLICY_FEED_API',
    source_name        VARCHAR(200) NULL COMMENT '피드 출처(파일명·기관명 등)',
    rule_action        VARCHAR(30)  NOT NULL DEFAULT 'BLOCK' COMMENT '배치 규칙 기본 조치 BLOCK/ALLOWLIST/REVIEW',
    default_priority   INT          NOT NULL DEFAULT 100,
    active             TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '배치 ON/OFF. OFF 시 하위 규칙 유효상태에 반영',
    total_rule_count   INT          NOT NULL DEFAULT 0,
    active_rule_count  INT          NOT NULL DEFAULT 0,
    memo               VARCHAR(2000) NULL,
    created_by         BIGINT       NULL,
    updated_by         BIGINT       NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_ip_block_batch_code (batch_code),
    KEY idx_admin_ip_block_batch_active (active, created_at),
    CONSTRAINT fk_admin_ip_block_batch_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_ip_block_batch_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 규칙 → 배치 FK(배치가 나중 생성이라 별도 ADD)
ALTER TABLE admin_security_block_rule
    ADD CONSTRAINT fk_admin_security_block_rule_batch FOREIGN KEY (batch_id) REFERENCES admin_ip_block_batch (id) ON DELETE SET NULL;

-- 3) 배치 작업(오퍼레이션) 이력
CREATE TABLE IF NOT EXISTS admin_ip_block_batch_operation (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    batch_id             BIGINT       NOT NULL,
    operation_type       VARCHAR(40)  NOT NULL COMMENT 'CREATE/IMPORT/TOGGLE_ON/TOGGLE_OFF',
    operation_option     VARCHAR(40)  NULL COMMENT 'cascade 전략: BATCH_ONLY/CASCADE_ACTIVE_RULES/RESTORE_BATCH_CONTROL/FORCE_ENABLE_ALL',
    requested_rule_count INT          NOT NULL DEFAULT 0,
    affected_rule_count  INT          NOT NULL DEFAULT 0,
    actor_user_id        BIGINT       NULL,
    memo                 VARCHAR(2000) NULL,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_ip_block_batch_op_batch (batch_id, created_at),
    CONSTRAINT fk_admin_ip_block_batch_op_batch FOREIGN KEY (batch_id) REFERENCES admin_ip_block_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_ip_block_batch_op_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 4) 배치 작업의 규칙 단위 before/after
CREATE TABLE IF NOT EXISTS admin_ip_block_batch_operation_rule (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    operation_id       BIGINT       NOT NULL,
    rule_id            BIGINT       NULL,
    operation_effect   VARCHAR(40)  NOT NULL COMMENT 'CREATED/ENABLED/DISABLED/SKIPPED_OVERRIDE',
    before_is_active   TINYINT(1)   NULL,
    after_is_active    TINYINT(1)   NULL,
    before_control_mode VARCHAR(30) NULL,
    after_control_mode VARCHAR(30)  NULL,
    memo               VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_admin_ip_block_batch_op_rule_op (operation_id),
    CONSTRAINT fk_admin_ip_block_batch_op_rule_op FOREIGN KEY (operation_id) REFERENCES admin_ip_block_batch_operation (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 5) 차단 접근 로그 — 인터셉터가 실제 차단한 요청 기록
CREATE TABLE IF NOT EXISTS admin_block_access_log (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    request_id         VARCHAR(64)  NOT NULL,
    user_id            BIGINT       NULL,
    request_uri        VARCHAR(512) NULL,
    http_method        VARCHAR(10)  NULL,
    block_kind         VARCHAR(20)  NOT NULL COMMENT 'IP/USER',
    block_match_type   VARCHAR(20)  NULL COMMENT 'SINGLE_IP/CIDR/RANGE/COUNTRY/ASN/ACCOUNT_STATUS',
    block_target_key   VARCHAR(160) NULL,
    block_rule_id      BIGINT       NULL,
    block_reason       VARCHAR(1000) NULL,
    client_ip          VARCHAR(64)  NULL,
    country_code       VARCHAR(8)   NULL,
    asn                VARCHAR(20)  NULL,
    cache_source       VARCHAR(20)  NULL,
    user_agent         VARCHAR(512) NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_block_access_log_time (created_at),
    KEY idx_admin_block_access_log_ip (client_ip, created_at),
    KEY idx_admin_block_access_log_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
