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
    email            VARCHAR(255) NOT NULL COMMENT '로그인 식별자로 사용하는 회원 이메일',
    password         VARCHAR(255) NULL COMMENT 'BCrypt로 암호화한 비밀번호 해시. 소셜 전용 계정은 NULL 가능',
    password_enabled TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '비밀번호 로그인 사용 여부. 소셜 전용 계정은 0',
    name             VARCHAR(100) NOT NULL,
    email_verified   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '이메일 인증 완료 여부',
    user_type        VARCHAR(20)  NOT NULL DEFAULT 'JOB_SEEKER', -- JOB_SEEKER/CAREER_CHANGER/EXPERIENCED
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '회원 권한. USER 또는 ADMIN',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '회원 상태. ACTIVE/DORMANT/BLOCKED/DELETED',
    plan             VARCHAR(20)  NOT NULL DEFAULT 'FREE',       -- FREE/BASIC/PRO/PREMIUM
    credit           INT          NOT NULL DEFAULT 0,
    last_login_at    DATETIME     NULL COMMENT '마지막 로그인 성공 시각',
    dormant_at       DATETIME     NULL COMMENT '휴면 계정으로 전환된 시각',
    blocked_reason   VARCHAR(255) NULL COMMENT '관리자가 회원을 차단한 사유',
    blocked_until    DATETIME     NULL COMMENT '기간 차단 만료 시각. NULL이면 무기한 또는 미차단',
    deleted_at       DATETIME     NULL COMMENT '회원 탈퇴 또는 삭제 처리 시각',
    status_changed_at DATETIME    NULL COMMENT '회원 상태가 마지막으로 변경된 시각',
    status_changed_by BIGINT      NULL COMMENT '회원 상태를 변경한 관리자 ID. 시스템 변경이면 NULL',
    failed_login_count INT        NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수',
    last_failed_login_at DATETIME NULL COMMENT '마지막 로그인 실패 시각',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status (status),
    KEY idx_users_status_changed_by (status_changed_by),
    CONSTRAINT fk_users_status_changed_by FOREIGN KEY (status_changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '회원 기본 정보와 로그인/권한/상태 관리 정보';

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
    user_id     BIGINT       NOT NULL COMMENT '토큰을 발급받은 회원 ID',
    token       VARCHAR(512) NOT NULL COMMENT '저장된 JWT refresh token 값',
    expired_at  DATETIME     NOT NULL COMMENT 'refresh token 만료 시각',
    revoked     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '토큰 폐기 여부',
    revoked_at  DATETIME     NULL COMMENT '토큰이 폐기된 시각',
    ip_address  VARCHAR(45)  NULL COMMENT '토큰 발급 요청 IP 주소',
    user_agent  VARCHAR(500) NULL COMMENT '토큰 발급 요청 User-Agent',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_token (token),
    KEY idx_refresh_token_user (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'JWT refresh token 저장 및 세션 감사 정보';

-- 로그인/로그아웃/토큰 갱신 감사 로그.
-- user_id는 실패 로그인처럼 사용자를 특정하지 못하는 이벤트를 위해 NULL 허용.
CREATE TABLE IF NOT EXISTS user_login_history (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NULL COMMENT '로그인 이벤트 대상 회원 ID. 실패로 회원 식별이 안 되면 NULL',
    event_type       VARCHAR(20)  NOT NULL COMMENT '인증 이벤트 유형. LOGIN/LOGOUT/REFRESH',
    auth_provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL' COMMENT '인증 제공자. LOCAL/KAKAO/NAVER/GOOGLE',
    login_method     VARCHAR(20)  NULL COMMENT '로그인 방식. EMAIL/OAUTH/REFRESH_TOKEN',
    login_identifier VARCHAR(255) NULL COMMENT '사용자가 입력한 로그인 식별자. 보통 이메일',
    success          TINYINT(1)   NOT NULL COMMENT '인증 성공 여부',
    fail_reason      VARCHAR(50)  NULL COMMENT '실패 사유. USER_NOT_FOUND/WRONG_PASSWORD/BLOCKED 등',
    ip_address       VARCHAR(45)  NULL COMMENT '요청 IP 주소',
    user_agent       VARCHAR(500) NULL COMMENT '요청 User-Agent',
    request_uri      VARCHAR(255) NULL COMMENT '인증 요청 URI',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_login_history_user (user_id),
    KEY idx_user_login_history_created (created_at),
    KEY idx_user_login_history_success (success),
    CONSTRAINT fk_user_login_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '로그인, 로그아웃, 토큰 갱신 감사 로그';

-- 관리자/시스템이 회원 상태를 바꾼 이력.
-- users.status의 현재값만으로는 과거 차단/휴면/해제 사유를 알 수 없으므로 별도 로그로 남긴다.
CREATE TABLE IF NOT EXISTS user_status_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '상태가 변경된 회원 ID',
    actor_user_id   BIGINT       NULL COMMENT '상태를 변경한 관리자 ID. 시스템 자동 변경이면 NULL',
    previous_status VARCHAR(20)  NULL COMMENT '변경 전 회원 상태',
    new_status      VARCHAR(20)  NOT NULL COMMENT '변경 후 회원 상태',
    reason          VARCHAR(255) NULL COMMENT '상태 변경 사유',
    memo            TEXT         NULL COMMENT '관리자 내부 메모',
    blocked_until   DATETIME     NULL COMMENT '차단 만료 시각. 차단 상태가 아니거나 무기한이면 NULL',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_status_history_user (user_id),
    KEY idx_user_status_history_actor (actor_user_id),
    KEY idx_user_status_history_created (created_at),
    CONSTRAINT fk_user_status_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_status_history_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '회원 상태 변경 이력';

-- 회원가입 및 설정 화면에서 수집하는 약관/개인정보/AI 데이터 활용 동의 이력.
-- 철회가 가능해야 하므로 현재값만 덮어쓰지 않고 변경 이벤트를 누적한다.
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
    UNIQUE KEY uk_job_posting_case_revision (application_case_id, revision),
    KEY idx_job_posting_case (application_case_id),
    CONSTRAINT fk_job_posting_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS application_case_extraction (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    application_case_id BIGINT NOT NULL,
    job_posting_id      BIGINT NULL,
    user_id             BIGINT NOT NULL,
    source_type         VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    active_status_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('QUEUED', 'RUNNING') THEN 1 ELSE NULL END
    ) STORED,
    error_message       VARCHAR(1000) NULL,
    started_at          DATETIME NULL,
    finished_at         DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_case_extraction_user_status (user_id, status, created_at, id),
    KEY idx_case_extraction_case_latest (application_case_id, created_at, id),
    KEY idx_case_extraction_job_posting (job_posting_id),
    UNIQUE KEY uk_case_extraction_active (application_case_id, active_status_marker),
    CONSTRAINT chk_case_extraction_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT fk_case_extraction_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE,
    CONSTRAINT fk_case_extraction_job_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id) ON DELETE SET NULL,
    CONSTRAINT fk_case_extraction_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
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
    condition_matrix         JSON NULL,                     -- 요구조건-스펙 비교 매트릭스(조건/유형/판정/근거)
    analysis_confidence      JSON NULL,                     -- 분석 신뢰도(level/입력 부족 사유)
    apply_decision           JSON NULL,                     -- 지원 판단 카드(APPLY/COMPLEMENT/HOLD + 이유·행동)
    model                    VARCHAR(80) NULL,
    prompt_version           VARCHAR(30) NULL,
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
    prompt_version  VARCHAR(30) NULL,
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

-- C 소유 대시보드 "오늘의 할 일". 파생(자동 계산) 할 일의 완료 오버라이드와 사용자가 직접 추가한
-- 할 일을 함께 저장한다(디자인 분석 §6.4 "오늘의 할 일 — 완료 처리"). derived_key가 NULL이면 사용자 추가 항목.
CREATE TABLE IF NOT EXISTS dashboard_todo (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    derived_key  VARCHAR(120) NULL,
    task         VARCHAR(500) NOT NULL,
    time_label   VARCHAR(50)  NOT NULL DEFAULT '오늘',
    done         TINYINT(1)   NOT NULL DEFAULT 0,
    completed_at DATETIME     NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dashboard_todo_derived (user_id, derived_key),
    KEY idx_dashboard_todo_user (user_id, created_at),
    CONSTRAINT fk_dashboard_todo_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- C 고도화 정규화 테이블은 운영 DB 점진 적용을 위해 patches/20260612_c_strategy_tables.sql 과 동일하게 관리한다.
CREATE TABLE IF NOT EXISTS fit_analysis_history (
    id BIGINT NOT NULL AUTO_INCREMENT, fit_analysis_id BIGINT NOT NULL, application_case_id BIGINT NOT NULL,
    previous_score INT NULL, new_score INT NULL, diff_summary JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id),
    UNIQUE KEY uk_fit_analysis_history_analysis (fit_analysis_id), KEY idx_fit_analysis_history_case (application_case_id, created_at),
    CONSTRAINT fk_fit_analysis_history_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE,
    CONSTRAINT fk_fit_analysis_history_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS fit_analysis_condition_match (
    id BIGINT NOT NULL AUTO_INCREMENT, fit_analysis_id BIGINT NOT NULL, condition_text VARCHAR(500) NOT NULL,
    condition_type VARCHAR(20) NOT NULL, match_status VARCHAR(20) NOT NULL, evidence VARCHAR(1000) NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', sort_order INT NOT NULL DEFAULT 0, PRIMARY KEY (id),
    KEY idx_fit_condition_analysis (fit_analysis_id, sort_order),
    CONSTRAINT fk_fit_condition_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS career_goal (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, target_job VARCHAR(255) NULL,
    target_period VARCHAR(100) NULL, priority_skill VARCHAR(255) NULL, preferred_company_type VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_career_goal_user (user_id),
    CONSTRAINT fk_career_goal_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS learning_plan (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, title VARCHAR(500) NOT NULL, target_skill VARCHAR(255) NOT NULL,
    start_date DATE NULL, end_date DATE NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_learning_plan_user (user_id, status, created_at),
    CONSTRAINT fk_learning_plan_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS learning_plan_task (
    id BIGINT NOT NULL AUTO_INCREMENT, learning_plan_id BIGINT NOT NULL, task VARCHAR(1000) NOT NULL,
    done TINYINT(1) NOT NULL DEFAULT 0, sort_order INT NOT NULL DEFAULT 0, completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_learning_plan_task_plan (learning_plan_id, sort_order),
    CONSTRAINT fk_learning_plan_task_plan FOREIGN KEY (learning_plan_id) REFERENCES learning_plan (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dashboard_insight (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, career_analysis_run_id BIGINT NULL, summary MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL, model VARCHAR(80) NULL, token_usage INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id), KEY idx_dashboard_insight_user (user_id, created_at),
    CONSTRAINT fk_dashboard_insight_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dashboard_insight_run FOREIGN KEY (career_analysis_run_id) REFERENCES career_analysis_run (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS analysis_quality_flag (
    id BIGINT NOT NULL AUTO_INCREMENT, target_type VARCHAR(40) NOT NULL, target_id BIGINT NOT NULL,
    flag_type VARCHAR(50) NOT NULL, severity VARCHAR(20) NOT NULL, memo VARCHAR(2000) NULL, resolved TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_analysis_quality_target_flag (target_type, target_id, flag_type),
    KEY idx_analysis_quality_resolved (resolved, severity, created_at)
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
    deleted_at          DATETIME NULL,                      -- soft delete: 사용자가 기록 삭제한 시각. NULL이면 활성
    last_resumed_at     DATETIME NULL,                      -- 복원(=복습)한 마지막 시각. 최근 기록 정렬·표시용
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

-- 멀티에이전트 면접 진행의 단계 트레이스 (각 에이전트의 행동·입출력 기록).
-- "AI 면접관이 무슨 판단을 했는지" 투명하게 보여주고, 향후 학습 데이터로도 쓴다.
CREATE TABLE IF NOT EXISTS interview_agent_step (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    interview_session_id BIGINT NOT NULL,
    question_id          BIGINT NULL,
    step_no              INT NOT NULL DEFAULT 0,
    agent                VARCHAR(30) NOT NULL,               -- PLANNER/EVALUATOR/CRITIC/PROBER/REPORTER/RETRIEVER/ORCHESTRATOR
    action               VARCHAR(60) NULL,
    status               VARCHAR(12) NULL,                   -- DONE/FAILED (running 은 프런트 표현)
    summary              MEDIUMTEXT NULL,                    -- 사람이 읽는 한 줄 요약
    detail               JSON NULL,                          -- 구조화 입출력
    elapsed_ms           INT NULL,                           -- 단계 소요 시간(ms)
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_agent_step_session (interview_session_id),
    CONSTRAINT fk_agent_step_session FOREIGN KEY (interview_session_id) REFERENCES interview_session (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 면접 평가 학습 데이터 (파인튜닝/평가 하니스용). 평가가 일어날 때마다 append.
-- 세션이 지워져도 학습 데이터는 남도록 FK 를 두지 않는다.
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

-- 면접 RAG 지식베이스 원본 (루브릭/기출/기업자료). 벡터는 Qdrant 에, 원본은 여기 보관.
CREATE TABLE IF NOT EXISTS interview_knowledge (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    kind       VARCHAR(30) NOT NULL,                       -- RUBRIC/QUESTION_BANK/COMPANY/GENERAL
    title      VARCHAR(255) NULL,
    content    MEDIUMTEXT NOT NULL,
    source     VARCHAR(255) NULL,
    indexed    TINYINT(1) NOT NULL DEFAULT 0,              -- Qdrant 색인 여부
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_interview_knowledge_kind (kind)
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
--  결제 / AI 사용량
-- =====================================================================
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

-- =====================================================================
--  커뮤니티
-- =====================================================================
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

CREATE TABLE IF NOT EXISTS post_ai_result (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    post_id       BIGINT       NOT NULL,
    task_type     VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    result_json   JSON         NULL,
    model         VARCHAR(80)  NULL,
    error_message VARCHAR(1000) NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at  DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_ai_result_task (post_id, task_type),
    KEY idx_post_ai_result_status (task_type, status, completed_at),
    CONSTRAINT fk_post_ai_result_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_moderation_setting (
    id             TINYINT      NOT NULL,
    strictness     VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    hide_threshold DECIMAL(3,2) NOT NULL DEFAULT 0.80,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_hide_threshold CHECK (hide_threshold BETWEEN 0.50 AND 0.95)
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

CREATE TABLE IF NOT EXISTS community_guideline (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    version_label VARCHAR(20)  NOT NULL,
    summary       VARCHAR(500) NULL,
    lede          TEXT         NULL,
    oks_json      JSON         NULL,
    nos_json      JSON         NULL,
    rules_json    JSON         NULL,
    params_json   JSON         NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    enforce_type  VARCHAR(20)  NOT NULL DEFAULT 'IMMEDIATE',
    scheduled_at  DATETIME     NULL,
    published_at  DATETIME     NULL,
    admin_id      BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_guideline_status (status, published_at DESC),
    CONSTRAINT fk_guideline_admin FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE SET NULL
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
    link_url     VARCHAR(200) NULL     COMMENT '관련 페이지 경로',
    link_label   VARCHAR(100) NULL     COMMENT '이동 버튼 라벨',
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

-- 알림 수신 설정(사용자별 1행). categories_json 에 비활성 카테고리만 false 로 저장.
CREATE TABLE IF NOT EXISTS notification_preference (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    push_enabled      TINYINT(1)  NOT NULL DEFAULT 1,
    email_enabled     TINYINT(1)  NOT NULL DEFAULT 1,
    categories_json   JSON        NULL,
    quiet_hours_start VARCHAR(5)  NULL,
    quiet_hours_end   VARCHAR(5)  NULL,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_preference_user (user_id),
    CONSTRAINT fk_notification_preference_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 푸시 구독(기기별). kind=WEB 은 web push endpoint+키, FCM/APNS 는 디바이스 토큰.
CREATE TABLE IF NOT EXISTS push_subscription (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    kind         VARCHAR(10)  NOT NULL,
    token        VARCHAR(700) NOT NULL,
    p256dh       VARCHAR(255) NULL,
    auth         VARCHAR(255) NULL,
    user_agent   VARCHAR(300) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_push_subscription_token (token(255)),
    KEY idx_push_subscription_user (user_id),
    CONSTRAINT fk_push_subscription_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


SET FOREIGN_KEY_CHECKS = 1;


