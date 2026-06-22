-- Persistent runtime AI settings for administrator-controlled fallback policy.

CREATE TABLE IF NOT EXISTS ai_runtime_setting (
    setting_key VARCHAR(80) NOT NULL,
    value_json  JSON NOT NULL,
    updated_by  BIGINT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key),
    CONSTRAINT fk_ai_runtime_setting_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
