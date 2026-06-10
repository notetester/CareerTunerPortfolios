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
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
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
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_token (token),
    KEY idx_refresh_token_user (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
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
    deadline_date DATE NULL,
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
    evidence            JSON NULL,
    ambiguous_conditions JSON NULL,
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
    verified_facts      JSON NULL,
    ai_inferences       JSON NULL,
    source_type         VARCHAR(30) NOT NULL DEFAULT 'JOB_POSTING',
    checked_at          DATETIME NULL,
    refresh_recommended_at DATETIME NULL,
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
    source_snapshot          JSON NULL,                     -- C 분석에 사용한 A/B 입력 식별·시점·요약
    score_basis              JSON NULL,                     -- 설명 가능한 점수 산정 근거
    gap_recommendations      JSON NULL,                     -- 필수 미충족/우대 보완/장기 성장 분류
    certificate_recommendations JSON NULL,                  -- 자격증 우선순위와 추천 이유
    strategy_actions         JSON NULL,                     -- 지원/보완/다음 준비 과제
    model                    VARCHAR(80) NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_message            VARCHAR(1000) NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fit_analysis_case (application_case_id),
    CONSTRAINT fk_fit_analysis_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- C 소유 학습 로드맵 체크리스트. fit_analysis 결과에서 생성하며 타 담당 원본 데이터는 수정하지 않는다.
CREATE TABLE IF NOT EXISTS fit_analysis_learning_task (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    fit_analysis_id     BIGINT NOT NULL,
    skill               VARCHAR(255) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    practice_task       VARCHAR(1000) NULL,
    expected_duration   VARCHAR(100) NULL,
    priority            VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    sort_order          INT NOT NULL DEFAULT 0,
    completed           TINYINT(1) NOT NULL DEFAULT 0,
    completed_at        DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fit_learning_task_analysis (fit_analysis_id),
    CONSTRAINT fk_fit_learning_task_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE
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

-- C 소유 장기 경향/대시보드 요약 실행 이력. 타 담당 원본은 입력 스냅샷으로만 참조한다.
CREATE TABLE IF NOT EXISTS career_analysis_run (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    analysis_type   VARCHAR(40) NOT NULL,                  -- CAREER_TREND/DASHBOARD_SUMMARY
    status          VARCHAR(20) NOT NULL,                  -- SUCCESS/FALLBACK/FAILED
    input_snapshot  JSON NULL,
    input_fingerprint VARCHAR(64) NULL,                    -- C 캐시 키: 입력이 동일하면 저장 결과 재사용(매 조회 AI 재실행 방지)
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

-- C 소유 장기 경향/대시보드 요약 실행 이력의 운영 메모. 적합도(admin_fit_analysis_memo)와 동일 패턴으로,
-- 관리자 완료 기준의 "분석 결과 운영 메모"(과도한 추천/잘못된 분석/사용자 문의 대응)를 실행 이력 단위로 남긴다.
CREATE TABLE IF NOT EXISTS admin_career_run_memo (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    career_analysis_run_id BIGINT NOT NULL,
    admin_user_id         BIGINT NOT NULL,
    memo_type             VARCHAR(30) NOT NULL DEFAULT 'GENERAL', -- GENERAL/QUALITY/USER_INQUIRY/REANALYSIS
    content               MEDIUMTEXT NOT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_career_memo_run (career_analysis_run_id),
    KEY idx_admin_career_memo_admin_user (admin_user_id),
    CONSTRAINT fk_admin_career_memo_run FOREIGN KEY (career_analysis_run_id) REFERENCES career_analysis_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_career_memo_admin_user FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE CASCADE
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
    parent_question_id   BIGINT NULL,                       -- 꼬리 질문이면 원 질문 id, 일반 질문이면 NULL
    question             MEDIUMTEXT NOT NULL,
    question_type        VARCHAR(30) NULL,                  -- EXPECTED/TECH/PERSONALITY/SITUATION/FOLLOW_UP
    sort_order           INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_interview_question_session (interview_session_id),
    KEY idx_interview_question_parent (parent_question_id),
    CONSTRAINT fk_interview_question_session FOREIGN KEY (interview_session_id) REFERENCES interview_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_question_parent FOREIGN KEY (parent_question_id) REFERENCES interview_question (id) ON DELETE CASCADE
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

-- 파일/스토리지 메타데이터 (음성/영상/문서 등 업로드 파일의 위치·종류를 기록).
-- 실제 바이트는 로컬 디스크(careertuner.uploads.media-dir)에 저장하고, 본 테이블은 메타만 보관한다.
CREATE TABLE IF NOT EXISTS file_asset (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    kind          VARCHAR(20) NOT NULL,                       -- AUDIO/VIDEO/RESUME/PORTFOLIO/POSTING/ATTACHMENT
    ref_type      VARCHAR(30) NULL,                           -- 연결 대상 종류 (예: INTERVIEW_ANSWER)
    ref_id        BIGINT NULL,                                -- 연결 대상 id
    original_name VARCHAR(255) NULL,
    content_type  VARCHAR(120) NULL,
    size_bytes    BIGINT NOT NULL DEFAULT 0,
    storage_key   VARCHAR(512) NOT NULL,                      -- 디스크 저장 경로/키 (예: media/12/uuid.webm)
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_file_asset_owner (owner_user_id),
    KEY idx_file_asset_ref (ref_type, ref_id),
    CONSTRAINT fk_file_asset_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE CASCADE
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

-- 커뮤니티 테이블 변경 (06-09)
CREATE TABLE IF NOT EXISTS community_post (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    category       VARCHAR(30)  NOT NULL,
    title          VARCHAR(255) NOT NULL,
    content        MEDIUMTEXT   NOT NULL,
    company_name   VARCHAR(255) NULL,
    job_title      VARCHAR(255) NULL,
    interview_type VARCHAR(30)  NULL,
    difficulty     VARCHAR(20)  NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    tags_json      JSON         NULL,
    is_anonymous   TINYINT(1)   NOT NULL DEFAULT 1,
    view_count     INT          NOT NULL DEFAULT 0,
    comment_count  INT          NOT NULL DEFAULT 0,
    like_count     INT          NOT NULL DEFAULT 0,
    bookmark_count INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_community_post_user (user_id),
    KEY idx_community_post_cat_status_created (category, status, created_at DESC),
    KEY idx_community_post_cat_like (category, status, like_count DESC),
    CONSTRAINT fk_community_post_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- AI요약 , 면접 질문 추출 , 실제 인터뷰 인증 마크 위해 면접후기 테이블 따로 생성
CREATE TABLE IF NOT EXISTS community_interview_review (
    post_id                   BIGINT        NOT NULL,
    application_case_id       BIGINT        NULL,
    company_name              VARCHAR(255)  NOT NULL,
    job_role                  VARCHAR(255)  NOT NULL,
    interview_type            VARCHAR(30)   NULL,
    difficulty                TINYINT       NULL,
    interview_date            DATE          NULL,
    result_status             VARCHAR(20)   NULL,
    questions_json            JSON          NULL,
    ai_summary_json           JSON          NULL,
    ai_extracted_questions    JSON          NULL,
    verification_status       VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    verification_evidence_url VARCHAR(512)  NULL,
    verification_confidence   DECIMAL(5,4)  NULL,
    verified_at               DATETIME      NULL,
    created_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id),
    KEY idx_interview_review_company (company_name),
    KEY idx_interview_review_role (job_role),
    CONSTRAINT fk_interview_review_post
    FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- 게시글 댓글
CREATE TABLE IF NOT EXISTS community_comment (

    id           BIGINT       NOT NULL AUTO_INCREMENT,
    post_id      BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    parent_id    BIGINT       NULL,
    content      MEDIUMTEXT   NOT NULL,
    is_anonymous TINYINT(1)   NOT NULL DEFAULT 1,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    like_count   INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_comment_post (post_id),
    KEY idx_comment_parent (parent_id),
    CONSTRAINT fk_comment_post   FOREIGN KEY (post_id)   REFERENCES community_post (id)      ON DELETE CASCADE,
    CONSTRAINT fk_comment_user   FOREIGN KEY (user_id)   REFERENCES users (id)                ON DELETE CASCADE,
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES community_comment (id)    ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 게시글 좋아요 반응
CREATE TABLE IF NOT EXISTS post_reaction (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    post_id        BIGINT       NOT NULL,
    reaction_type  VARCHAR(20)  NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_reaction (user_id, post_id, reaction_type),
    KEY idx_pr_post (post_id),
    CONSTRAINT fk_pr_user FOREIGN KEY (user_id) REFERENCES users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_pr_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- 댓글 좋아요 반응
CREATE TABLE IF NOT EXISTS comment_reaction (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    comment_id     BIGINT       NOT NULL,
    reaction_type  VARCHAR(20)  NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_reaction (user_id, comment_id, reaction_type),
    KEY idx_cr_comment (comment_id),
    CONSTRAINT fk_cr_user    FOREIGN KEY (user_id)    REFERENCES users (id)              ON DELETE CASCADE,
    CONSTRAINT fk_cr_comment FOREIGN KEY (comment_id) REFERENCES community_comment (id)  ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- 게시글 신고
CREATE TABLE IF NOT EXISTS post_report (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    reporter_id    BIGINT        NULL,
    post_id        BIGINT        NOT NULL,
    reason         VARCHAR(50)   NOT NULL,
    detail         VARCHAR(500)  NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    action_taken   VARCHAR(20)   NULL,
    ai_label       VARCHAR(50)   NULL,
    ai_confidence  DECIMAL(5,4)  NULL,
    admin_id       BIGINT        NULL,
    resolved_at    DATETIME      NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_report (reporter_id, post_id),
    KEY idx_pr_post_status (post_id, status),
    KEY idx_pr_status (status),
    CONSTRAINT fk_pr_reporter FOREIGN KEY (reporter_id) REFERENCES users (id)          ON DELETE SET NULL,
    CONSTRAINT fk_pr_rpost    FOREIGN KEY (post_id)     REFERENCES community_post (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 댓글 신고
CREATE TABLE IF NOT EXISTS comment_report (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    reporter_id    BIGINT        NULL,
    comment_id     BIGINT        NOT NULL,
    reason         VARCHAR(50)   NOT NULL,
    detail         VARCHAR(500)  NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    action_taken   VARCHAR(20)   NULL,
    ai_label       VARCHAR(50)   NULL,
    ai_confidence  DECIMAL(5,4)  NULL,
    admin_id       BIGINT        NULL,
    resolved_at    DATETIME      NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_report (reporter_id, comment_id),
    KEY idx_cr_comment_status (comment_id, status),
    KEY idx_cr_status (status),
    CONSTRAINT fk_cr_reporter FOREIGN KEY (reporter_id) REFERENCES users (id)             ON DELETE SET NULL,
    CONSTRAINT fk_cr_rcomment FOREIGN KEY (comment_id)  REFERENCES community_comment (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS community_tag (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    usage_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- 커뮤니티 태그
CREATE TABLE IF NOT EXISTS community_post_tag (
    post_id    BIGINT       NOT NULL,
    tag_id     BIGINT       NOT NULL,
    is_ai      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, tag_id),
    KEY idx_post_tag_tag (tag_id),
    CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_tag_tag  FOREIGN KEY (tag_id)  REFERENCES community_tag (id)  ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

--- 문의테이블
CREATE TABLE IF NOT EXISTS notice (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    title         VARCHAR(255) NOT NULL,
    content       MEDIUMTEXT   NOT NULL,
    category      VARCHAR(30)  NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    is_pinned     TINYINT(1)   NOT NULL DEFAULT 0,
    thumbnail_url VARCHAR(512) NULL,
    admin_id      BIGINT       NULL,
    view_count    INT          NOT NULL DEFAULT 0,
    published_at  DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notice_list (status, is_pinned DESC, published_at DESC),
    CONSTRAINT fk_notice_admin FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE SET NULL
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

--  faq / faq_media
CREATE TABLE IF NOT EXISTS faq (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    question     VARCHAR(500) NOT NULL,
    answer       MEDIUMTEXT   NOT NULL,
    category     VARCHAR(30)  NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    is_published TINYINT(1)   NOT NULL DEFAULT 1,
    admin_id     BIGINT       NULL,
    view_count   INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_faq_list (category, is_published, sort_order),
    CONSTRAINT fk_faq_admin FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE SET NULL
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- 순서가 있는 여러장의 사진 , youtube 링크
CREATE TABLE IF NOT EXISTS faq_media (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    faq_id      BIGINT        NOT NULL,
    media_type  VARCHAR(20)   NOT NULL,
    media_url   VARCHAR(512)  NOT NULL,
    sort_order  INT           NOT NULL DEFAULT 0,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_faq_media_faq (faq_id, sort_order),
    CONSTRAINT fk_faq_media_faq FOREIGN KEY (faq_id) REFERENCES faq (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 9. support_ticket
CREATE TABLE IF NOT EXISTS support_ticket (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    category   VARCHAR(30)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    priority   VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ticket_user (user_id, created_at DESC),
    KEY idx_ticket_status (status, priority, created_at),
    CONSTRAINT fk_ticket_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 10. support_ticket_message
CREATE TABLE IF NOT EXISTS support_ticket_message (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT      NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    sender_id   BIGINT      NULL,
    content     MEDIUMTEXT  NOT NULL,
    is_internal TINYINT(1)  NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ticket_msg_thread (ticket_id, created_at),
    CONSTRAINT fk_ticket_msg_ticket FOREIGN KEY (ticket_id) REFERENCES support_ticket (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_msg_sender FOREIGN KEY (sender_id) REFERENCES users (id)          ON DELETE SET NULL
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 11. notification link는 백엔드에서 주입
CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    actor_id    BIGINT       NULL,
    type        VARCHAR(40)  NOT NULL,
    target_type VARCHAR(20)  NULL,
    target_id   BIGINT       NULL,
    title       VARCHAR(255) NOT NULL,
    message     TEXT         NULL,
    link        VARCHAR(512) NULL,
    is_read     TINYINT(1)   NOT NULL DEFAULT 0,
    read_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notification_user_unread (user_id, is_read, created_at DESC),
    KEY idx_notification_user_type (user_id, type, created_at DESC),
    KEY idx_notification_target (target_type, target_id),
    CONSTRAINT fk_notification_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


SET FOREIGN_KEY_CHECKS = 1;


