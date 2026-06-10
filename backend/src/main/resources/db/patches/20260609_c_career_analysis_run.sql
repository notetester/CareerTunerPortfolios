-- C 장기 경향/대시보드 AI 실행 이력.
-- D 면접과 A/B 원본은 읽기 전용 입력으로만 사용하며 구조를 수정하지 않는다.

CREATE TABLE IF NOT EXISTS career_analysis_run (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    analysis_type   VARCHAR(40) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    input_snapshot  JSON NULL,
    result          JSON NULL,
    model           VARCHAR(80) NULL,
    input_tokens    INT NOT NULL DEFAULT 0,
    output_tokens   INT NOT NULL DEFAULT 0,
    token_usage     INT NOT NULL DEFAULT 0,
    error_message   VARCHAR(1000) NULL,
    retryable       TINYINT(1) NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_career_analysis_run_user_type (user_id, analysis_type, created_at),
    KEY idx_career_analysis_run_status (status, created_at),
    CONSTRAINT fk_career_analysis_run_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
