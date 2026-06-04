-- =====================================================================
--  CareerTuner — MySQL 스키마 초안 (기획.txt §13 기반)
--  실행: mysql -u root -p < schema.sql   (또는 IntelliJ Database 콘솔)
--  ※ 스켈레톤 단계의 1차안이며, 도메인 구현하며 컬럼이 조정될 수 있다.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS careertuner
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE careertuner;

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 사용자 / 프로필
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NULL,                       -- 소셜 로그인은 NULL 가능
    name          VARCHAR(100) NOT NULL,
    user_type     VARCHAR(20)  NOT NULL DEFAULT 'JOB_SEEKER', -- JOB_SEEKER/CAREER_CHANGER/EXPERIENCED/ADMIN
    plan          VARCHAR(20)  NOT NULL DEFAULT 'FREE',     -- FREE/BASIC/PRO/PREMIUM
    credit        INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS user_profile (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    desired_job      VARCHAR(255) NULL,
    desired_industry VARCHAR(255) NULL,
    education        JSON NULL,            -- [{school, major, graduation}]
    career           JSON NULL,            -- [{company, role, period, duties}]
    projects         JSON NULL,            -- [{name, role, stack, result}]
    skills           JSON NULL,            -- ["React","Spring",...]
    certificates     JSON NULL,            -- ["정보처리기사","SQLD",...]
    languages        JSON NULL,            -- [{test, score}]
    portfolio_links  JSON NULL,            -- ["https://github.com/..",..]
    resume_text      MEDIUMTEXT NULL,
    self_intro       MEDIUMTEXT NULL,
    preferences      JSON NULL,            -- {salary, region, workType}
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_user (user_id),
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 지원 건 (핵심 단위) 및 분석 결과들
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS application_case (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    company_name  VARCHAR(255) NOT NULL,
    job_title     VARCHAR(255) NOT NULL,
    posting_date  DATE NULL,
    source_type   VARCHAR(20) NOT NULL DEFAULT 'TEXT',     -- TEXT/PDF/IMAGE/URL/MANUAL
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',    -- DRAFT/ANALYZING/READY/APPLIED/CLOSED
    is_favorite   TINYINT(1) NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_application_case_user (user_id),
    CONSTRAINT fk_application_case_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS job_posting (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    original_text       MEDIUMTEXT NULL,
    uploaded_file_url   VARCHAR(512) NULL,
    extracted_text      MEDIUMTEXT NULL,
    source_type         VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_posting_case (application_case_id),
    CONSTRAINT fk_job_posting_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS job_analysis (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    employment_type     VARCHAR(50) NULL,
    experience_level    VARCHAR(50) NULL,
    required_skills     JSON NULL,
    preferred_skills    JSON NULL,
    duties              MEDIUMTEXT NULL,
    qualifications      MEDIUMTEXT NULL,
    difficulty          VARCHAR(20) NULL,                    -- EASY/NORMAL/HARD
    summary             MEDIUMTEXT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_analysis_case (application_case_id),
    CONSTRAINT fk_job_analysis_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS company_analysis (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    company_summary     MEDIUMTEXT NULL,
    recent_issues       MEDIUMTEXT NULL,
    industry            VARCHAR(100) NULL,
    competitors         JSON NULL,
    interview_points    MEDIUMTEXT NULL,
    sources             JSON NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_company_analysis_case (application_case_id),
    CONSTRAINT fk_company_analysis_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS fit_analysis (
    id                       BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id      BIGINT NOT NULL,
    fit_score                INT NULL,                       -- 0~100
    matched_skills           JSON NULL,
    missing_skills           JSON NULL,
    recommended_study        JSON NULL,
    recommended_certificates JSON NULL,
    strategy                 MEDIUMTEXT NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fit_analysis_case (application_case_id),
    CONSTRAINT fk_fit_analysis_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 면접
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS interview_session (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    mode                VARCHAR(30) NOT NULL,               -- BASIC/JOB/PERSONALITY/PRESSURE/REAL/RESUME/PORTFOLIO/COMPANY
    started_at          DATETIME NULL,
    ended_at            DATETIME NULL,
    total_score         INT NULL,
    report              JSON NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_interview_session_case (application_case_id),
    CONSTRAINT fk_interview_session_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS interview_question (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    interview_session_id BIGINT NOT NULL,
    question             MEDIUMTEXT NOT NULL,
    question_type        VARCHAR(30) NULL,                  -- EXPECTED/TECH/PERSONALITY/SITUATION/FOLLOW_UP
    sort_order           INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_interview_question_session (interview_session_id),
    CONSTRAINT fk_interview_question_session FOREIGN KEY (interview_session_id) REFERENCES interview_session (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS interview_answer (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    question_id     BIGINT NOT NULL,
    answer_text     MEDIUMTEXT NULL,
    audio_url       VARCHAR(512) NULL,
    video_url       VARCHAR(512) NULL,
    score           INT NULL,
    feedback        MEDIUMTEXT NULL,
    improved_answer MEDIUMTEXT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_interview_answer_question (question_id),
    CONSTRAINT fk_interview_answer_question FOREIGN KEY (question_id) REFERENCES interview_question (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 커뮤니티 / 결제 / AI 사용량
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_post (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    user_id        BIGINT NOT NULL,
    category       VARCHAR(30) NOT NULL,                    -- JOB_REVIEW/INTERVIEW_REVIEW/QNA/CERT/PORTFOLIO/FREE
    title          VARCHAR(255) NOT NULL,
    content        MEDIUMTEXT NOT NULL,
    company_name   VARCHAR(255) NULL,
    job_title      VARCHAR(255) NULL,
    interview_type VARCHAR(30) NULL,                        -- FIRST/SECOND/EXECUTIVE/TECH
    difficulty     VARCHAR(20) NULL,
    is_anonymous   TINYINT(1) NOT NULL DEFAULT 1,
    view_count     INT NOT NULL DEFAULT 0,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_community_post_user (user_id),
    KEY idx_community_post_category (category),
    CONSTRAINT fk_community_post_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS payment (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    amount        INT NOT NULL,
    plan          VARCHAR(20) NULL,
    credit_amount INT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING/PAID/FAILED/REFUNDED
    paid_at       DATETIME NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_payment_user (user_id),
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS ai_usage_log (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    user_id             BIGINT NOT NULL,
    application_case_id BIGINT NULL,
    feature_type        VARCHAR(40) NOT NULL,               -- JOB_ANALYSIS/COMPANY_RESEARCH/QUESTION_GEN/INTERVIEW/...
    token_usage         INT NULL,
    credit_used         INT NOT NULL DEFAULT 0,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_usage_user (user_id),
    KEY idx_ai_usage_case (application_case_id),
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_usage_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE SET NULL
) ENGINE = InnoDB;

SET FOREIGN_KEY_CHECKS = 1;
