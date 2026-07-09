-- C 고도화: 성장 추적, 목표/학습 계획, 대시보드 캐시, 품질 플래그 정규화

SET @fit_prompt_column_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'prompt_version'
    ),
    'SELECT 1',
    'ALTER TABLE fit_analysis ADD COLUMN prompt_version VARCHAR(30) NULL AFTER model'
);
PREPARE fit_prompt_column_stmt FROM @fit_prompt_column_sql;
EXECUTE fit_prompt_column_stmt;
DEALLOCATE PREPARE fit_prompt_column_stmt;

SET @career_prompt_column_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'career_analysis_run' AND column_name = 'prompt_version'
    ),
    'SELECT 1',
    'ALTER TABLE career_analysis_run ADD COLUMN prompt_version VARCHAR(30) NULL AFTER model'
);
PREPARE career_prompt_column_stmt FROM @career_prompt_column_sql;
EXECUTE career_prompt_column_stmt;
DEALLOCATE PREPARE career_prompt_column_stmt;

CREATE TABLE IF NOT EXISTS fit_analysis_history (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    fit_analysis_id     BIGINT NOT NULL,
    application_case_id BIGINT NOT NULL,
    previous_score      INT NULL,
    new_score           INT NULL,
    diff_summary        JSON NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fit_analysis_history_analysis (fit_analysis_id),
    KEY idx_fit_analysis_history_case (application_case_id, created_at),
    CONSTRAINT fk_fit_analysis_history_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE,
    CONSTRAINT fk_fit_analysis_history_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS fit_analysis_condition_match (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    fit_analysis_id BIGINT NOT NULL,
    condition_text  VARCHAR(500) NOT NULL,
    condition_type  VARCHAR(20) NOT NULL,
    match_status    VARCHAR(20) NOT NULL,
    evidence        VARCHAR(1000) NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    sort_order      INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_fit_condition_analysis (fit_analysis_id, sort_order),
    CONSTRAINT fk_fit_condition_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS career_goal (
    id                     BIGINT NOT NULL AUTO_INCREMENT,
    user_id                BIGINT NOT NULL,
    target_job             VARCHAR(255) NULL,
    target_period          VARCHAR(100) NULL,
    priority_skill         VARCHAR(255) NULL,
    preferred_company_type VARCHAR(255) NULL,
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_career_goal_user (user_id),
    CONSTRAINT fk_career_goal_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS learning_plan (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    title        VARCHAR(500) NOT NULL,
    target_skill VARCHAR(255) NOT NULL,
    start_date   DATE NULL,
    end_date     DATE NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_learning_plan_user (user_id, status, created_at),
    CONSTRAINT fk_learning_plan_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS learning_plan_task (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    learning_plan_id BIGINT NOT NULL,
    task             VARCHAR(1000) NOT NULL,
    done             TINYINT(1) NOT NULL DEFAULT 0,
    sort_order       INT NOT NULL DEFAULT 0,
    completed_at     DATETIME NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_learning_plan_task_plan (learning_plan_id, sort_order),
    CONSTRAINT fk_learning_plan_task_plan FOREIGN KEY (learning_plan_id) REFERENCES learning_plan (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dashboard_insight (
    id                     BIGINT NOT NULL AUTO_INCREMENT,
    user_id                BIGINT NOT NULL,
    career_analysis_run_id BIGINT NULL,
    summary                MEDIUMTEXT NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    model                  VARCHAR(80) NULL,
    token_usage            INT NOT NULL DEFAULT 0,
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dashboard_insight_user (user_id, created_at),
    CONSTRAINT fk_dashboard_insight_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dashboard_insight_run FOREIGN KEY (career_analysis_run_id) REFERENCES career_analysis_run (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS analysis_quality_flag (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(40) NOT NULL,
    target_id   BIGINT NOT NULL,
    flag_type   VARCHAR(50) NOT NULL,
    severity    VARCHAR(20) NOT NULL,
    memo        VARCHAR(2000) NULL,
    resolved    TINYINT(1) NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_analysis_quality_target_flag (target_type, target_id, flag_type),
    KEY idx_analysis_quality_resolved (resolved, severity, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
