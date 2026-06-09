-- =====================================================================
--  CareerTuner — MySQL 스키마 (기획.txt §13 + 회원/인증 확장)
--  대상 DB는 외부에서 제공된다(개발: team1_db). 따라서 CREATE DATABASE/USE 없이
--  연결된 스키마에 테이블만 생성한다. 적용: db/apply 참고 또는 IntelliJ Database 콘솔.
--  utf8mb4 전제. 재실행 가능(IF NOT EXISTS).
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
--  회원 / 인증
-- =====================================================================
CREATE TABLE IF NOT EXISTS users (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    email            VARCHAR(255) NOT NULL,
    password         VARCHAR(255) NULL,                          -- BCrypt 해시. 소셜 전용 계정은 NULL
    password_enabled TINYINT(1)   NOT NULL DEFAULT 1,            -- 비밀번호 로그인 가능 여부(소셜 전용=0)
    name             VARCHAR(100) NOT NULL,
    email_verified   TINYINT(1)   NOT NULL DEFAULT 0,            -- 이메일 인증 완료 여부
    user_type        VARCHAR(20)  NOT NULL DEFAULT 'JOB_SEEKER', -- JOB_SEEKER/CAREER_CHANGER/EXPERIENCED
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER',       -- USER/ADMIN
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',     -- ACTIVE/DORMANT/BLOCKED/DELETED
    plan             VARCHAR(20)  NOT NULL DEFAULT 'FREE',       -- FREE/BASIC/PRO/PREMIUM
    credit           INT          NOT NULL DEFAULT 0,
    last_login_at    DATETIME     NULL,
    dormant_at       DATETIME     NULL,                          -- 휴면 전환 시각
    blocked_reason   VARCHAR(255) NULL,                          -- 관리자 차단 사유
    blocked_until    DATETIME     NULL,                          -- 기간 차단 만료 시각(NULL이면 무기한/미차단)
    deleted_at       DATETIME     NULL,                          -- 탈퇴/삭제 처리 시각
    status_changed_at DATETIME    NULL,                          -- 회원 상태 마지막 변경 시각
    status_changed_by BIGINT      NULL,                          -- 상태를 변경한 관리자 id, 시스템 변경이면 NULL
    failed_login_count INT        NOT NULL DEFAULT 0,             -- 연속 로그인 실패 횟수
    last_failed_login_at DATETIME NULL,                           -- 마지막 로그인 실패 시각
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status (status),
    KEY idx_users_status_changed_by (status_changed_by),
    CONSTRAINT fk_users_status_changed_by FOREIGN KEY (status_changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 한 유저가 여러 소셜 계정 연동 가능 (provider별 1개)
CREATE TABLE IF NOT EXISTS user_social (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL,                      -- KAKAO/NAVER/GOOGLE
    provider_user_id VARCHAR(255) NOT NULL,                      -- 제공자가 발급한 고유 사용자 ID
    linked_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_social_provider (provider, provider_user_id),
    KEY idx_user_social_user (user_id),
    CONSTRAINT fk_user_social_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 이메일 인증 / 비밀번호 재설정 토큰
CREATE TABLE IF NOT EXISTS email_verification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NULL,
    email       VARCHAR(255) NOT NULL,
    token       VARCHAR(255) NOT NULL,                           -- UUID
    purpose     VARCHAR(20)  NOT NULL DEFAULT 'VERIFY',          -- VERIFY/RESET_PW
    expired_at  DATETIME     NOT NULL,
    used        TINYINT(1)   NOT NULL DEFAULT 0,
    used_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token (token),
    KEY idx_email_verification_email (email),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- JWT refresh token (회전/폐기 관리용)
CREATE TABLE IF NOT EXISTS refresh_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(512) NOT NULL,
    expired_at  DATETIME     NOT NULL,
    revoked     TINYINT(1)   NOT NULL DEFAULT 0,
    revoked_at  DATETIME     NULL,
    ip_address  VARCHAR(45)  NULL,
    user_agent  VARCHAR(500) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_token (token),
    KEY idx_refresh_token_user (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 로그인/로그아웃/토큰 갱신 감사 로그.
-- user_id는 실패 로그인처럼 사용자를 특정하지 못하는 이벤트를 위해 NULL 허용.
CREATE TABLE IF NOT EXISTS user_login_history (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NULL,
    event_type       VARCHAR(20)  NOT NULL,                      -- LOGIN/LOGOUT/REFRESH
    auth_provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',      -- LOCAL/KAKAO/NAVER/GOOGLE
    login_method     VARCHAR(20)  NULL,                          -- EMAIL/OAUTH/REFRESH_TOKEN
    login_identifier VARCHAR(255) NULL,                          -- 사용자가 입력한 이메일 등
    success          TINYINT(1)   NOT NULL,
    fail_reason      VARCHAR(50)  NULL,                          -- USER_NOT_FOUND/WRONG_PASSWORD/BLOCKED 등
    ip_address       VARCHAR(45)  NULL,
    user_agent       VARCHAR(500) NULL,
    request_uri      VARCHAR(255) NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_login_history_user (user_id),
    KEY idx_user_login_history_created (created_at),
    KEY idx_user_login_history_success (success),
    CONSTRAINT fk_user_login_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 관리자/시스템이 회원 상태를 바꾼 이력.
-- users.status의 현재값만으로는 과거 차단/휴면/해제 사유를 알 수 없으므로 별도 로그로 남긴다.
CREATE TABLE IF NOT EXISTS user_status_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    actor_user_id   BIGINT       NULL,
    previous_status VARCHAR(20)  NULL,
    new_status      VARCHAR(20)  NOT NULL,
    reason          VARCHAR(255) NULL,
    memo            TEXT         NULL,
    blocked_until   DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_status_history_user (user_id),
    KEY idx_user_status_history_actor (actor_user_id),
    KEY idx_user_status_history_created (created_at),
    CONSTRAINT fk_user_status_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_status_history_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
--  프로필
-- =====================================================================
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
--  지원 건 (핵심 단위) 및 분석 결과들
-- =====================================================================
CREATE TABLE IF NOT EXISTS application_case (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    company_name  VARCHAR(255) NOT NULL,
    job_title     VARCHAR(255) NOT NULL,
    posting_date  DATE NULL,
    source_type   VARCHAR(20) NOT NULL DEFAULT 'TEXT',     -- TEXT/PDF/IMAGE/URL/MANUAL
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',    -- DRAFT/ANALYZING/READY/APPLIED/CLOSED
    is_favorite   TINYINT(1) NOT NULL DEFAULT 0,
    archived_at   DATETIME NULL,
    deleted_at    DATETIME NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_application_case_user (user_id),
    CONSTRAINT fk_application_case_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS job_posting (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    revision            INT NOT NULL DEFAULT 1,
    original_text       MEDIUMTEXT NULL,
    uploaded_file_url   VARCHAR(512) NULL,
    extracted_text      MEDIUMTEXT NULL,
    source_type         VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_posting_case (application_case_id),
    CONSTRAINT fk_job_posting_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS job_analysis (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    job_posting_id      BIGINT NULL,
    job_posting_revision INT NULL,
    employment_type     VARCHAR(50) NULL,
    experience_level    VARCHAR(50) NULL,
    required_skills     JSON NULL,
    preferred_skills    JSON NULL,
    duties              MEDIUMTEXT NULL,
    qualifications      MEDIUMTEXT NULL,
    difficulty          VARCHAR(20) NULL,                    -- EASY/NORMAL/HARD
    summary             MEDIUMTEXT NULL,
    confirmed_at        DATETIME NULL,
    admin_memo          VARCHAR(2000) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_analysis_case (application_case_id),
    KEY idx_job_analysis_posting (job_posting_id),
    CONSTRAINT fk_job_analysis_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_analysis_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS company_analysis (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    job_posting_id      BIGINT NULL,
    job_posting_revision INT NULL,
    company_summary     MEDIUMTEXT NULL,
    recent_issues       MEDIUMTEXT NULL,
    industry            VARCHAR(100) NULL,
    competitors         JSON NULL,
    interview_points    MEDIUMTEXT NULL,
    sources             JSON NULL,
    confirmed_at        DATETIME NULL,
    admin_memo          VARCHAR(2000) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_company_analysis_case (application_case_id),
    KEY idx_company_analysis_posting (job_posting_id),
    CONSTRAINT fk_company_analysis_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_analysis_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS application_case_status_history (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    changed_by_user_id  BIGINT NULL,
    previous_status     VARCHAR(20) NULL,
    new_status          VARCHAR(20) NOT NULL,
    memo                VARCHAR(1000) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_application_case_status_history_case (application_case_id),
    CONSTRAINT fk_application_case_status_history_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE,
    CONSTRAINT fk_application_case_status_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS admin_fit_analysis_memo (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    fit_analysis_id BIGINT NOT NULL,
    admin_user_id   BIGINT NOT NULL,
    memo_type       VARCHAR(30) NOT NULL DEFAULT 'GENERAL', -- GENERAL/QUALITY/USER_INQUIRY/REANALYSIS
    content         MEDIUMTEXT NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_fit_memo_fit_analysis (fit_analysis_id),
    KEY idx_admin_fit_memo_admin_user (admin_user_id),
    CONSTRAINT fk_admin_fit_memo_fit_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_fit_memo_admin_user FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
--  면접
-- =====================================================================
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS interview_question (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    interview_session_id BIGINT NOT NULL,
    question             MEDIUMTEXT NOT NULL,
    question_type        VARCHAR(30) NULL,                  -- EXPECTED/TECH/PERSONALITY/SITUATION/FOLLOW_UP
    sort_order           INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_interview_question_session (interview_session_id),
    CONSTRAINT fk_interview_question_session FOREIGN KEY (interview_session_id) REFERENCES interview_session (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
--  커뮤니티 / 결제 / AI 사용량
-- =====================================================================
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_usage_log (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    user_id             BIGINT NOT NULL,
    application_case_id BIGINT NULL,
    feature_type        VARCHAR(40) NOT NULL,               -- JOB_ANALYSIS/COMPANY_RESEARCH/QUESTION_GEN/INTERVIEW/...
    status              VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    model               VARCHAR(80) NULL,
    input_tokens        INT NULL,
    output_tokens       INT NULL,
    token_usage         INT NULL,
    credit_used         INT NOT NULL DEFAULT 0,
    error_message       VARCHAR(1000) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_usage_user (user_id),
    KEY idx_ai_usage_case (application_case_id),
    KEY idx_ai_usage_feature (feature_type),
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_usage_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
