-- =====================================================================
--  D 담당(가상 면접) 스키마 마이그레이션 — 2026-06-10
--  기존 DB(team1_db)에 적용. schema.sql 재실행으로는 안 되는 변경(특히 컬럼 추가) 포함.
--
--  적용 대상:
--   1) 신규 테이블 4개 (CREATE TABLE IF NOT EXISTS — 재실행 안전)
--       - file_asset                : 음성/영상/문서 업로드 메타
--       - interview_agent_step      : 멀티에이전트 진행 trace
--       - interview_knowledge       : RAG 지식베이스 원본
--       - interview_training_sample : 평가 학습 데이터(파인튜닝)
--   2) interview_question.parent_question_id 컬럼 추가 (꼬리질문 ↔ 원질문 연결)
--       - CREATE TABLE IF NOT EXISTS 로는 절대 안 들어감 → 아래 가드형 ALTER 로 추가
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 적용 전 백업 권장.
--  실행: mysql -h <host> -u <user> -p <db> < 20260610_d_interview_schema.sql
-- =====================================================================

-- ── 1) 신규 테이블 ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS file_asset (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    kind          VARCHAR(20) NOT NULL,                       -- AUDIO/VIDEO/RESUME/PORTFOLIO/POSTING/ATTACHMENT
    ref_type      VARCHAR(30) NULL,
    ref_id        BIGINT NULL,
    original_name VARCHAR(255) NULL,
    content_type  VARCHAR(120) NULL,
    size_bytes    BIGINT NOT NULL DEFAULT 0,
    storage_key   VARCHAR(512) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_file_asset_owner (owner_user_id),
    KEY idx_file_asset_ref (ref_type, ref_id),
    CONSTRAINT fk_file_asset_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS interview_agent_step (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    interview_session_id BIGINT NOT NULL,
    question_id          BIGINT NULL,
    step_no              INT NOT NULL DEFAULT 0,
    agent                VARCHAR(30) NOT NULL,                -- PLANNER/EVALUATOR/CRITIC/RETRIEVER/REPORTER/ORCHESTRATOR
    action               VARCHAR(60) NULL,
    summary              MEDIUMTEXT NULL,
    detail               JSON NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_agent_step_session (interview_session_id),
    CONSTRAINT fk_agent_step_session FOREIGN KEY (interview_session_id) REFERENCES interview_session (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS interview_knowledge (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    kind       VARCHAR(30) NOT NULL,                          -- RUBRIC/QUESTION_BANK/COMPANY/GENERAL
    title      VARCHAR(255) NULL,
    content    MEDIUMTEXT NOT NULL,
    source     VARCHAR(255) NULL,
    indexed    TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_interview_knowledge_kind (kind)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS interview_training_sample (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    interview_session_id BIGINT NULL,
    question_id          BIGINT NULL,
    question             MEDIUMTEXT NOT NULL,
    answer_text          MEDIUMTEXT NOT NULL,
    score                INT NOT NULL,
    feedback             MEDIUMTEXT NULL,
    rag_used             TINYINT(1) NOT NULL DEFAULT 0,
    model                VARCHAR(80) NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_training_session (interview_session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ── 2) interview_question.parent_question_id 추가 (가드형 — 재실행/중복 적용 안전) ──

SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'interview_question'
      AND COLUMN_NAME = 'parent_question_id'
);
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE interview_question
        ADD COLUMN parent_question_id BIGINT NULL AFTER interview_session_id,
        ADD KEY idx_interview_question_parent (parent_question_id),
        ADD CONSTRAINT fk_interview_question_parent
            FOREIGN KEY (parent_question_id) REFERENCES interview_question (id) ON DELETE CASCADE',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SELECT TABLE_NAME FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = DATABASE()
--     AND TABLE_NAME IN ('file_asset','interview_agent_step','interview_knowledge','interview_training_sample');
-- SHOW COLUMNS FROM interview_question LIKE 'parent_question_id';
