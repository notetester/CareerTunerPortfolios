-- key-value 런타임 설정 콘솔 + 변경 이력. TripTogether APPLICATION_RUNTIME_SETTING(_HISTORY) 이식.
-- DB 값을 우선 참조하고, 비면 fallback → 코드 기본값 순. 모든 변경은 actor·before/after·버전으로 이력화.

CREATE TABLE IF NOT EXISTS application_runtime_setting (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    setting_key    VARCHAR(160) NOT NULL COMMENT '설정 키(예: oauth.kakao.client-id)',
    setting_group  VARCHAR(60)  NOT NULL DEFAULT 'GENERAL',
    display_name   VARCHAR(160) NOT NULL,
    setting_value  TEXT         NULL COMMENT 'DB 우선 설정값',
    fallback_value TEXT         NULL COMMENT '설정값이 비면 쓸 fallback',
    value_type     VARCHAR(30)  NOT NULL DEFAULT 'STRING' COMMENT 'STRING/NUMBER/BOOLEAN/URL/SECRET',
    secret         TINYINT(1)   NOT NULL DEFAULT 0,
    editable       TINYINT(1)   NOT NULL DEFAULT 1,
    active         TINYINT(1)   NOT NULL DEFAULT 1,
    description    VARCHAR(1000) NULL,
    updated_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_runtime_setting_key (setting_key),
    KEY idx_application_runtime_setting_group (setting_group, active),
    CONSTRAINT fk_application_runtime_setting_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS application_runtime_setting_history (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    setting_id          BIGINT       NULL,
    setting_key         VARCHAR(160) NOT NULL,
    version_no          INT          NOT NULL DEFAULT 1,
    change_type         VARCHAR(30)  NOT NULL COMMENT 'CREATE/UPDATE/IMPORT/RESET',
    actor_user_id       BIGINT       NULL,
    before_value        TEXT         NULL,
    after_value         TEXT         NULL,
    before_fallback     TEXT         NULL,
    after_fallback      TEXT         NULL,
    before_snapshot     JSON         NULL,
    after_snapshot      JSON         NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_application_runtime_setting_history_key (setting_key, created_at),
    CONSTRAINT fk_application_runtime_setting_history_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
