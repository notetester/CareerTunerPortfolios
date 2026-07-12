-- 관리자 fit-analysis DB-fixture 통합테스트용 최소 스키마(H2 호환).
-- 운영 schema.sql(MySQL 전용 구문 포함)에서 admin 매퍼가 참조하는 컬럼만 발췌했다.
-- JSON 컬럼은 매퍼가 문자열로 다루므로 CLOB 로 둔다. 컬럼명·의미는 운영 스키마와 동일하게 유지할 것.

CREATE TABLE IF NOT EXISTS users (
    id         BIGINT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS application_case (
    id           BIGINT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    job_title    VARCHAR(255) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'READY',
    is_favorite  TINYINT NOT NULL DEFAULT 0,
    archived_at  TIMESTAMP NULL,
    deleted_at   TIMESTAMP NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fit_analysis (
    id                          BIGINT PRIMARY KEY,
    application_case_id         BIGINT NOT NULL,
    fit_score                   INT NULL,
    matched_skills              CLOB NULL,
    missing_skills              CLOB NULL,
    recommended_study           CLOB NULL,
    recommended_certificates    CLOB NULL,
    strategy                    CLOB NULL,
    source_snapshot             CLOB NULL,
    score_basis                 CLOB NULL,
    gap_recommendations         CLOB NULL,
    certificate_recommendations CLOB NULL,
    strategy_actions            CLOB NULL,
    condition_matrix            CLOB NULL,
    analysis_confidence         CLOB NULL,
    apply_decision              CLOB NULL,
    model                       VARCHAR(80) NULL,
    prompt_version              VARCHAR(30) NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_message               VARCHAR(1000) NULL,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fit_analysis_gate_result (
    id                    BIGINT PRIMARY KEY,
    fit_analysis_id       BIGINT NOT NULL,
    gate_status           VARCHAR(20) NOT NULL,
    needs_human_review    TINYINT NOT NULL DEFAULT 0,
    reason_count          INT NOT NULL DEFAULT 0,
    max_severity          VARCHAR(20) NULL,
    gate_reasons_json     CLOB NULL,
    evidence_gate_version VARCHAR(40) NOT NULL,
    rag_runtime_enabled   TINYINT NOT NULL DEFAULT 0,
    rewrite_applied       TINYINT NOT NULL DEFAULT 0,
    review_status         VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reviewed_by           BIGINT NULL,
    reviewed_at           TIMESTAMP NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_fit_analysis_memo (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    fit_analysis_id BIGINT NOT NULL,
    admin_user_id   BIGINT NOT NULL,
    memo_type       VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    content         CLOB NOT NULL,
    deleted_at      TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fit_analysis_learning_task (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    fit_analysis_id   BIGINT NOT NULL,
    skill             VARCHAR(255) NOT NULL,
    title             VARCHAR(500) NOT NULL,
    practice_task     VARCHAR(1000) NULL,
    expected_duration VARCHAR(100) NULL,
    priority          VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    sort_order        INT NOT NULL DEFAULT 0,
    completed         TINYINT NOT NULL DEFAULT 0,
    completed_at      TIMESTAMP NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
