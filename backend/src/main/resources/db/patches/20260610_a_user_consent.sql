-- A auth consent table.
-- Stores signup/settings consent history for terms, privacy, AI data usage, and marketing.

CREATE TABLE IF NOT EXISTS user_consent (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL COMMENT '동의 주체 회원 ID',
    consent_type VARCHAR(40) NOT NULL COMMENT '동의 유형. TERMS/PRIVACY/AI_DATA/MARKETING',
    agreed       TINYINT(1)  NOT NULL COMMENT '동의 여부',
    agreed_at    DATETIME    NULL COMMENT '동의한 시각',
    revoked_at   DATETIME    NULL COMMENT '철회한 시각',
    source       VARCHAR(40) NULL COMMENT '동의가 발생한 위치. REGISTER/SETTINGS 등',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_consent_user (user_id),
    KEY idx_user_consent_type (consent_type),
    CONSTRAINT fk_user_consent_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '회원 동의 및 철회 이력';
