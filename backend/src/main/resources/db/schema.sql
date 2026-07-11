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
    email            VARCHAR(255) NULL COMMENT 'Login and recovery email. ID-only signup users can register and verify it later.',
    login_id         VARCHAR(50)  NULL COMMENT '로그인 아이디(문자열, 선택·전역 UNIQUE·설정 후 변경 불가 정책)',
    phone            VARCHAR(40)  NULL COMMENT '전화번호(선택, 전역 UNIQUE)',
    phone_verified   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '전화번호 인증 여부(선택·스텁)',
    password         VARCHAR(255) NULL COMMENT 'BCrypt로 암호화한 비밀번호 해시. 소셜 전용 계정은 NULL 가능',
    password_enabled TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '비밀번호 로그인 사용 여부. 소셜 전용 계정은 0',
    name             VARCHAR(100) NOT NULL,
    email_verified   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '이메일 인증 완료 여부',
    user_type        VARCHAR(20)  NOT NULL DEFAULT 'JOB_SEEKER', -- JOB_SEEKER/CAREER_CHANGER/EXPERIENCED
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '회원 권한. USER/ADMIN/SUPER_ADMIN',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '회원 상태. ACTIVE/DORMANT/BLOCKED/DELETED',
    plan             VARCHAR(20)  NOT NULL DEFAULT 'FREE',       -- FREE/BASIC/PRO/PREMIUM
    credit           INT          NOT NULL DEFAULT 0,
    activity_point   INT          NOT NULL DEFAULT 0 COMMENT '누적 활동 포인트(리워드 레벨 산정용 XP)',
    user_level       INT          NOT NULL DEFAULT 1 COMMENT '현재 활동 레벨(user_level_policy 참조)',
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
    UNIQUE KEY uk_users_login_id (login_id),
    UNIQUE KEY uk_users_phone (phone),
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
    UNIQUE KEY uk_user_social_user_provider (user_id, provider),
    KEY idx_user_social_user (user_id),
    CONSTRAINT fk_user_social_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 이메일 인증 / 비밀번호 재설정 토큰
CREATE TABLE IF NOT EXISTS email_verification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NULL,
    email       VARCHAR(255) NOT NULL,
    token       VARCHAR(255) NOT NULL,                           -- UUID
    purpose     VARCHAR(20)  NOT NULL DEFAULT 'VERIFY',          -- VERIFY/EMAIL_CHANGE/RESET_PW/FIND_ID/DORMANT_RELEASE
    frontend_client VARCHAR(32) NOT NULL DEFAULT 'primary',      -- 결과를 돌려보낼 named frontend client
    expired_at  DATETIME     NOT NULL,
    used        TINYINT(1)   NOT NULL DEFAULT 0,
    used_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token (token),
    KEY idx_email_verification_email (email),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 전화번호 SMS OTP 인증 코드. 로그인 사용자가 전화번호 소유를 검증할 때 사용.
-- 실 제공자 키가 있으면 실 발송, 없으면 Mock 제공자가 코드만 로깅하고 devCode 로 반환한다.
CREATE TABLE IF NOT EXISTS sms_otp_code (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL COMMENT '인증을 요청한 회원',
    phone         VARCHAR(40)  NOT NULL COMMENT '인증 대상 전화번호',
    code          VARCHAR(10)  NOT NULL COMMENT '발송한 6자리 OTP 코드',
    attempt_count INT          NOT NULL DEFAULT 0 COMMENT '검증 시도 횟수',
    max_attempts  INT          NOT NULL DEFAULT 5 COMMENT '허용 최대 검증 시도 횟수',
    expires_at    DATETIME     NOT NULL COMMENT '코드 만료 시각',
    verified_at   DATETIME     NULL COMMENT '검증 성공 시각. NULL이면 미검증',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sms_otp_user_phone (user_id, phone),
    KEY idx_sms_otp_expires (expires_at),
    CONSTRAINT fk_sms_otp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '전화번호 SMS OTP 인증 코드';

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

-- 네이티브 소셜 제공자 응답을 앱의 PKCE verifier와 일회성으로 교환한다.
-- verifier 검증 전에는 users/user_social을 만들지 않고, handoff 원문도 DB에 저장하지 않는다.
CREATE TABLE IF NOT EXISTS native_auth_handoff (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    provider          VARCHAR(20)  NOT NULL COMMENT 'KAKAO/NAVER/GOOGLE',
    provider_user_id  VARCHAR(255) NOT NULL COMMENT '제공자가 발급한 고유 사용자 ID',
    email             VARCHAR(255) NULL COMMENT '제공자가 반환한 이메일. 미제공 시 NULL',
    email_verified    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '제공자가 명시적으로 보증한 이메일 검증 여부',
    display_name      VARCHAR(100) NULL COMMENT '제공자가 반환한 이름. 미제공 시 NULL',
    code_hash         CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'handoffCode의 SHA-256 base64url hash',
    handoff_challenge CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256(handoffVerifier)의 base64url 값',
    expired_at        DATETIME     NOT NULL COMMENT '교환 만료 시각(발급 후 3분)',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_native_auth_handoff_code_hash (code_hash),
    KEY idx_native_auth_handoff_expiry (expired_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '네이티브 OAuth PKCE 일회성 토큰 교환';

-- 로그인/로그아웃/토큰 갱신 감사 로그.
-- user_id는 실패 로그인처럼 사용자를 특정하지 못하는 이벤트를 위해 NULL 허용.
CREATE TABLE IF NOT EXISTS user_login_history (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NULL COMMENT '로그인 이벤트 대상 회원 ID. 실패로 회원 식별이 안 되면 NULL',
    event_type       VARCHAR(20)  NOT NULL COMMENT '인증 이벤트 유형. LOGIN/LOGOUT/REFRESH',
    auth_provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL' COMMENT '인증 제공자. LOCAL/KAKAO/NAVER/GOOGLE',
    login_method     VARCHAR(20)  NULL COMMENT '로그인 방식. EMAIL/OAUTH/REFRESH_TOKEN',
    login_identifier VARCHAR(255) NULL COMMENT '사용자가 입력한 로그인 식별자. 아이디 또는 이메일',
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
    consent_type VARCHAR(40) NOT NULL COMMENT '동의 유형. TERMS/PRIVACY/AI_DATA/RESUME_ANALYSIS/MARKETING',
    consent_version VARCHAR(40) NOT NULL COMMENT '동의 시점의 법적 문서 버전',
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

-- 프로필 AI 분석 산출물(A영역) 영속 — 사용자 조회 + C 적합도 입력 창구. feature_type 별 최신 1행 upsert.
CREATE TABLE IF NOT EXISTS profile_ai_analysis (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    feature_type       VARCHAR(40) NOT NULL,   -- PROFILE_SUMMARY / PROFILE_SKILL_EXTRACT / PROFILE_COMPLETENESS
    summary            MEDIUMTEXT NULL,
    strengths          JSON NULL,
    gaps               JSON NULL,
    recommendations    JSON NULL,
    extracted_skills   JSON NULL,
    criteria           JSON NULL,
    job_family         VARCHAR(60) NULL,
    completeness_score INT NULL,
    ai_score           INT NULL,
    quality_warnings   JSON NULL,
    model              VARCHAR(120) NULL,
    status             VARCHAR(20) NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_ai_user_feature (user_id, feature_type),
    CONSTRAINT fk_profile_ai_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
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
    ocr_requested_provider VARCHAR(20) NULL,                 -- 선택 OCR provider 스냅샷 (CLAUDE/OPENAI/SELF_OCR)
    status              VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    active_status_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('QUEUED', 'RUNNING') THEN 1 ELSE NULL END
    ) STORED,
    error_message       VARCHAR(1000) NULL,
    extraction_strategy VARCHAR(40) NULL,
    quality_score       INT NULL,
    quality_status      VARCHAR(30) NULL,
    quality_report_json JSON NULL,
    model_versions_json JSON NULL,
    fallback_eligible   TINYINT(1) NOT NULL DEFAULT 0,
    fallback_reason     VARCHAR(255) NULL,
    reviewed_at         DATETIME NULL,
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
    CONSTRAINT chk_case_extraction_quality_status CHECK (quality_status IS NULL OR quality_status IN ('PASS', 'REVIEW_REQUIRED', 'FAILED')),
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
    requested_provider  VARCHAR(20) NULL,                    -- 선택 provider (LOCAL/CLAUDE/OPENAI)
    actual_provider     VARCHAR(20) NULL,                    -- 실제 성공 provider
    actual_model        VARCHAR(80) NULL,                    -- 실제 모델 ID
    fallback_used       TINYINT(1) NULL,                     -- 폴백 여부(NULL=legacy/unknown)
    attempt_path        JSON NULL,                           -- 시도한 provider 순서·결과
    run_mode            VARCHAR(20) NULL,                    -- INITIAL/MANUAL (legacy=NULL)
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
    requested_provider  VARCHAR(20) NULL,                    -- 선택 provider (OPENAI/CLAUDE/LOCAL)
    actual_provider     VARCHAR(20) NULL,                    -- 실제 성공 provider
    actual_model        VARCHAR(80) NULL,                    -- 실제 모델 ID
    fallback_used       TINYINT(1) NULL,                     -- 폴백 여부(NULL=legacy/unknown)
    attempt_path        JSON NULL,                           -- 시도한 provider 순서·결과
    run_mode            VARCHAR(20) NULL,                    -- INITIAL/MANUAL (legacy=NULL)
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_company_analysis_case (application_case_id),
    KEY idx_company_analysis_posting (job_posting_id),
    CONSTRAINT fk_company_analysis_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_analysis_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 지원 건별 초기 실행 프로필(모델 선택·재실행 Phase 1). 초기 자동 파이프라인의 "중복 진입 방지 + 늦은 완료
-- fencing"을 제공한다(외부 모델 호출 포함이라 엄밀한 exactly-once 는 아니다). worker 추출성공/최초확정 경로가
-- state 를 PENDING→RUNNING 으로 조건부 claim 하고, execution_token 으로 stale-reaper 의 늦은 갱신을 fencing 한다.
-- OCR 선택값은 실행 주체(추출 워커)가 달라 application_case_extraction.ocr_requested_provider 에 스냅샷한다.
CREATE TABLE IF NOT EXISTS application_case_initial_run (
    application_case_id       BIGINT NOT NULL,
    state                     VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/RUNNING/DONE/FAILED
    job_analysis_provider     VARCHAR(20) NULL,                       -- LOCAL/CLAUDE/OPENAI (미선택=NULL → 현행 체인)
    company_analysis_provider VARCHAR(20) NULL,                       -- OPENAI/CLAUDE/LOCAL
    execution_token           CHAR(36) NULL,                          -- RUNNING claim 시 UUID 발급(fencing 토큰)
    started_at                DATETIME NULL,
    finished_at               DATETIME NULL,
    failure_reason            VARCHAR(255) NULL,
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (application_case_id),
    KEY idx_initial_run_stale (state, started_at),
    CONSTRAINT chk_initial_run_state CHECK (state IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT fk_initial_run_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 기업분석 웹검색 결과 캐시(235 §4·§6). 같은 회사 재검색 방지(비용↓)와 신선도 판정 근거.
-- query_key = 정규화된 회사 식별 쿼리, results = 스니펫+URL 목록(JSON), fetched_at = 수집 시각(TTL 기준).
CREATE TABLE IF NOT EXISTS company_search_cache (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    query_key  VARCHAR(255) NOT NULL,
    results    JSON NULL,
    fetched_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_search_cache_query (query_key)
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
    certificate_evidence     JSON NULL,                     -- 자격증 근거 snapshot(생성 시 1회 수집, 읽기는 DB만; 공식 출처·상태·메시지)
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
    deleted_at      DATETIME NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_fit_memo_fit_analysis (fit_analysis_id),
    KEY idx_admin_fit_memo_admin_user (admin_user_id),
    KEY idx_admin_fit_memo_deleted (deleted_at),
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
    deleted_at            DATETIME NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_career_memo_run (career_analysis_run_id),
    KEY idx_admin_career_memo_admin_user (admin_user_id),
    KEY idx_admin_career_memo_deleted (deleted_at),
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
-- ⚠ 감사 기록 테이블: 생성 시점의 점수 변화·diff 를 기록한다. 사용자 히스토리 API 는 fit_analysis 행에서
--    실시간 계산하므로 현재 읽기 소비자는 없다(의도된 append-only 감사 로그 — 회색지대 QA 에서 처분 확정).
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

-- C 소유 R3 review-first evidence gate. patches/20260629_c_fit_analysis_evidence_gate.sql 과 동일하게 관리한다.
-- 적합도 AI 설명의 결정론 후처리 안전층(점수/판단·원본 미변경, E1 grounding guard 위의 soft review 층).
CREATE TABLE IF NOT EXISTS fit_analysis_evidence_source (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    fit_analysis_id BIGINT       NOT NULL,
    source_type     VARCHAR(40)  NOT NULL,                 -- userEvidence/jobRequirements/catalogFacts/companyContext
    user_owned      TINYINT(1)   NOT NULL DEFAULT 0,
    item_count      INT          NOT NULL DEFAULT 0,
    items_json      JSON         NULL,                     -- 축약 스킬/근거 목록(원문·개인정보 제외)
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fit_evidence_source_analysis (fit_analysis_id, source_type),
    CONSTRAINT fk_fit_evidence_source_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS fit_analysis_gate_result (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    fit_analysis_id       BIGINT       NOT NULL,
    gate_status           VARCHAR(20)  NOT NULL,            -- PASSED/REVIEW_REQUIRED/REJECTED
    needs_human_review    TINYINT(1)   NOT NULL DEFAULT 0,
    reason_count          INT          NOT NULL DEFAULT 0,
    max_severity          VARCHAR(20)  NULL,                -- warning/critical
    gate_reasons_json     JSON         NULL,                -- [{type,claim,reason,severity}] 축약(개인정보 제외)
    evidence_gate_version VARCHAR(40)  NOT NULL,
    rag_runtime_enabled   TINYINT(1)   NOT NULL DEFAULT 0,
    rewrite_applied       TINYINT(1)   NOT NULL DEFAULT 0,
    review_status         VARCHAR(30)  NOT NULL DEFAULT 'PENDING', -- PENDING/RESOLVED/REANALYSIS_REQUESTED (운영자 gate review workflow)
    reviewed_by           BIGINT       NULL,
    reviewed_at           DATETIME     NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fit_gate_result_analysis (fit_analysis_id),
    KEY idx_fit_gate_result_status (gate_status, created_at),
    KEY idx_fit_gate_result_review (review_status, created_at),
    CONSTRAINT fk_fit_gate_result_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE
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

CREATE TABLE IF NOT EXISTS planner_memo (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NULL,
    content TEXT NULL,
    color VARCHAR(20) NOT NULL DEFAULT 'yellow',
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    overlay_visible TINYINT(1) NOT NULL DEFAULT 0,
    opacity DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    application_case_id BIGINT NULL,
    fit_analysis_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_planner_memo_user (user_id, pinned, updated_at),
    KEY idx_planner_memo_case (application_case_id),
    KEY idx_planner_memo_fit (fit_analysis_id),
    CONSTRAINT fk_planner_memo_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_planner_memo_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE SET NULL,
    CONSTRAINT fk_planner_memo_fit FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS planner_schedule_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    kind VARCHAR(30) NOT NULL DEFAULT 'TASK',
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    all_day TINYINT(1) NOT NULL DEFAULT 0,
    timing_precision VARCHAR(20) NOT NULL DEFAULT 'MINUTE',
    start_at DATETIME NOT NULL,
    end_at DATETIME NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    application_case_id BIGINT NULL,
    fit_analysis_id BIGINT NULL,
    source_type VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    source_ref VARCHAR(120) NULL,
    source_snapshot_json JSON NULL,
    overlay_visible TINYINT(1) NOT NULL DEFAULT 0,
    opacity DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    click_through TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_planner_schedule_user_time (user_id, start_at, end_at),
    KEY idx_planner_schedule_case (application_case_id, start_at),
    KEY idx_planner_schedule_fit (fit_analysis_id),
    KEY idx_planner_schedule_source (source_type, source_ref),
    CONSTRAINT fk_planner_schedule_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_planner_schedule_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE SET NULL,
    CONSTRAINT fk_planner_schedule_fit FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS planner_schedule_reminder (
    id BIGINT NOT NULL AUTO_INCREMENT,
    schedule_item_id BIGINT NOT NULL,
    remind_at DATETIME NOT NULL,
    offset_minutes INT NULL,
    channels_json JSON NULL,
    sound_enabled TINYINT(1) NOT NULL DEFAULT 1,
    vibration_enabled TINYINT(1) NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_planner_reminder_due (status, remind_at),
    KEY idx_planner_reminder_item (schedule_item_id, remind_at),
    CONSTRAINT fk_planner_reminder_item FOREIGN KEY (schedule_item_id) REFERENCES planner_schedule_item (id) ON DELETE CASCADE
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
    admin_memo          TEXT NULL,                          -- 관리자 운영 메모(운영자 참고용, 사용자 미노출)
    PRIMARY KEY (id),
    KEY idx_interview_session_case (application_case_id),
    CONSTRAINT fk_interview_session_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS interview_question (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    interview_session_id BIGINT NOT NULL,
    parent_question_id   BIGINT NULL,                       -- 꼬리 질문이면 원 질문 id, 일반 질문이면 NULL
    question             MEDIUMTEXT NOT NULL,
    model_answer         MEDIUMTEXT NULL,                   -- 모범답안(답안지), 채점 기준 (patch 20260612_d 동기화)
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
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_interview_knowledge_kind (kind),
    KEY idx_interview_knowledge_deleted (deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 파일/스토리지 메타데이터 (음성/영상/문서 등 업로드 파일의 위치·종류를 기록).
-- 실제 바이트는 로컬 디스크(careertuner.uploads.media-dir)에 저장하고, 본 테이블은 메타만 보관한다.
CREATE TABLE IF NOT EXISTS file_asset (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    kind          VARCHAR(20) NOT NULL,                       -- AUDIO/VIDEO/RESUME/PORTFOLIO/POSTING/ATTACHMENT
    ref_type      VARCHAR(30) NULL,                           -- 연결 대상 종류 (예: INTERVIEW_ANSWER/USER_PROFILE_PORTFOLIO)
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
--  데스크톱 협업 / 친구 / 1:1 대화
-- =====================================================================
CREATE TABLE IF NOT EXISTS collaboration_friend_request (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    requester_id   BIGINT      NOT NULL,
    receiver_id    BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    pending_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
    ) STORED,
    responded_at   DATETIME    NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_friend_request_pending (requester_id, receiver_id, pending_marker),
    KEY idx_collab_friend_request_receiver (receiver_id, status, created_at DESC),
    KEY idx_collab_friend_request_requester (requester_id, status, created_at DESC),
    CONSTRAINT chk_collab_friend_request_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED')),
    CONSTRAINT fk_collab_friend_request_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_friend_request_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '사용자 간 친구 요청';

CREATE TABLE IF NOT EXISTS collaboration_friendship (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    user_id        BIGINT   NOT NULL,
    friend_user_id BIGINT   NOT NULL,
    created_by     BIGINT   NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_friendship_pair (user_id, friend_user_id),
    KEY idx_collab_friendship_friend (friend_user_id),
    CONSTRAINT fk_collab_friendship_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_friendship_friend FOREIGN KEY (friend_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_friendship_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '수락된 친구 관계. 조회 단순화를 위해 양방향 행을 저장한다';

CREATE TABLE IF NOT EXISTS collaboration_conversation (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    type          VARCHAR(20)  NOT NULL DEFAULT 'DIRECT',
    user_low_id   BIGINT       NULL,
    user_high_id  BIGINT       NULL,
    title         VARCHAR(120) NULL,
    description   VARCHAR(500) NULL,
    image_file_id   BIGINT        NULL COMMENT '방 프로필 사진(file_asset)',
    notice          VARCHAR(1000) NULL COMMENT '방 공지',
    invite_policy   VARCHAR(20)  NOT NULL DEFAULT 'ALL_MEMBERS' COMMENT '초대 권한. OWNER_ONLY/MANAGERS/SPECIFIC_MEMBERS/ALL_MEMBERS',
    allow_anonymous TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '익명 참가 허용',
    anonymous_only  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '익명만 참가 가능',
    password_hash VARCHAR(255) NULL,
    max_members   INT          NOT NULL DEFAULT 100,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_direct_pair (type, user_low_id, user_high_id),
    KEY idx_collab_conversation_type_updated (type, updated_at DESC),
    KEY idx_collab_conversation_created_by (created_by, updated_at DESC),
    CONSTRAINT chk_collab_conversation_type CHECK (type IN ('DIRECT', 'GROUP', 'PUBLIC', 'PRIVATE')),
    CONSTRAINT chk_collab_conversation_invite_policy CHECK (invite_policy IN ('OWNER_ONLY', 'MANAGERS', 'SPECIFIC_MEMBERS', 'ALL_MEMBERS')),
    CONSTRAINT fk_collab_conversation_image FOREIGN KEY (image_file_id) REFERENCES file_asset (id) ON DELETE SET NULL,
    CONSTRAINT fk_collab_conversation_low FOREIGN KEY (user_low_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_high FOREIGN KEY (user_high_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '1:1, 그룹, 공개, 비공개 메신저 대화방';

CREATE TABLE IF NOT EXISTS collaboration_conversation_member (
    conversation_id      BIGINT      NOT NULL,
    user_id              BIGINT      NOT NULL,
    role                 VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    invited_by           BIGINT      NULL,
    last_read_message_id BIGINT      NULL,
    muted                TINYINT(1)  NOT NULL DEFAULT 0,
    anonymous            TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '이 방에 익명으로 참가',
    room_nickname        VARCHAR(60) NULL COMMENT '방 전용 닉네임(익명/방전용 프로필)',
    room_profile_file_id BIGINT      NULL COMMENT '방 전용 프로필 사진(file_asset)',
    joined_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_read_at         DATETIME    NULL,
    left_at              DATETIME    NULL,
    PRIMARY KEY (conversation_id, user_id),
    KEY idx_collab_conversation_member_user (user_id, status, joined_at DESC),
    KEY idx_collab_conversation_member_inviter (invited_by),
    CONSTRAINT chk_collab_conversation_member_role CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER')),
    CONSTRAINT chk_collab_conversation_member_status CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    CONSTRAINT fk_collab_conversation_member_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_member_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_member_inviter FOREIGN KEY (invited_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_collab_conv_member_room_profile FOREIGN KEY (room_profile_file_id) REFERENCES file_asset (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '대화방 참여자와 읽음 상태';

-- 방 관리자 세부 권한·재입장불가 강퇴·초대 허용 멤버·방 활동 로그 (patches/20260705b_conversation_settings.sql)
CREATE TABLE IF NOT EXISTS collaboration_conversation_permission (
    conversation_id     BIGINT     NOT NULL,
    user_id             BIGINT     NOT NULL,
    can_kick            TINYINT(1) NOT NULL DEFAULT 0,
    can_ban             TINYINT(1) NOT NULL DEFAULT 0,
    can_set_password    TINYINT(1) NOT NULL DEFAULT 0,
    can_invite          TINYINT(1) NOT NULL DEFAULT 0,
    can_edit_room       TINYINT(1) NOT NULL DEFAULT 0,
    can_manage_members  TINYINT(1) NOT NULL DEFAULT 0,
    granted_by          BIGINT     NULL,
    created_at          DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    KEY idx_collab_conv_permission_user (user_id),
    CONSTRAINT fk_collab_conv_permission_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_permission_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_permission_granted_by FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '방 관리자(MANAGER) 세부 권한 플래그';

CREATE TABLE IF NOT EXISTS collaboration_conversation_ban (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    banned_by       BIGINT       NULL,
    reason          VARCHAR(500) NULL,
    deleted_at      DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_conv_ban (conversation_id, user_id),
    KEY idx_collab_conv_ban_user (user_id),
    KEY idx_collab_conv_ban_deleted (deleted_at),
    CONSTRAINT fk_collab_conv_ban_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_ban_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_ban_banned_by FOREIGN KEY (banned_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '재입장불가 강퇴(ban) 명단';

CREATE TABLE IF NOT EXISTS collaboration_conversation_invite_allow (
    conversation_id BIGINT   NOT NULL,
    user_id         BIGINT   NOT NULL,
    granted_by      BIGINT   NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_collab_conv_invite_allow_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_invite_allow_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_invite_allow_granted_by FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SPECIFIC_MEMBERS 초대 허용 멤버';

CREATE TABLE IF NOT EXISTS collaboration_conversation_audit (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    actor_id        BIGINT       NULL,
    target_user_id  BIGINT       NULL,
    action          VARCHAR(50)  NOT NULL,
    detail          VARCHAR(1000) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_collab_conv_audit_conversation (conversation_id, id DESC),
    CONSTRAINT fk_collab_conv_audit_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_collab_conv_audit_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '방 활동 로그';

CREATE TABLE IF NOT EXISTS collaboration_conversation_invite (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    inviter_id      BIGINT      NULL,
    invitee_id      BIGINT      NOT NULL,
    anonymous       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '익명 초대 여부',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    pending_marker  TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
    ) STORED,
    responded_at    DATETIME    NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_conversation_invite_pending (conversation_id, invitee_id, pending_marker),
    KEY idx_collab_conversation_invite_invitee (invitee_id, status, created_at DESC),
    CONSTRAINT chk_collab_conversation_invite_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED')),
    CONSTRAINT fk_collab_conversation_invite_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_invite_inviter FOREIGN KEY (inviter_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_collab_conversation_invite_invitee FOREIGN KEY (invitee_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '그룹/비공개 채팅방 참가 초대';

CREATE TABLE IF NOT EXISTS collaboration_message (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    sender_id       BIGINT      NOT NULL,
    kind            VARCHAR(20) NOT NULL DEFAULT 'CHAT',
    content         MEDIUMTEXT  NULL,
    deleted_at      DATETIME    NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_collab_message_thread (conversation_id, id),
    KEY idx_collab_message_sender (sender_id, created_at DESC),
    CONSTRAINT chk_collab_message_kind CHECK (kind IN ('CHAT', 'NOTE')),
    CONSTRAINT fk_collab_message_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_message_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '대화방 메시지. CHAT과 NOTE를 kind로 구분한다';

CREATE TABLE IF NOT EXISTS collaboration_message_attachment (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    message_id    BIGINT      NOT NULL,
    file_asset_id BIGINT      NOT NULL,
    share_mode    VARCHAR(20) NOT NULL DEFAULT 'TEMPORARY',
    expires_at    DATETIME    NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_message_attachment (message_id, file_asset_id),
    KEY idx_collab_message_attachment_file (file_asset_id),
    KEY idx_collab_message_attachment_expiry (share_mode, expires_at),
    CONSTRAINT chk_collab_message_attachment_mode CHECK (share_mode IN ('TEMPORARY', 'CLOUD', 'LOCAL')),
    CONSTRAINT fk_collab_message_attachment_message FOREIGN KEY (message_id) REFERENCES collaboration_message (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_message_attachment_file FOREIGN KEY (file_asset_id) REFERENCES file_asset (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '대화 메시지 첨부 파일과 공유 정책';

CREATE TABLE IF NOT EXISTS collaboration_message_posting (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    message_id          BIGINT   NOT NULL,
    application_case_id BIGINT   NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_message_posting (message_id, application_case_id),
    KEY idx_collab_message_posting_case (application_case_id),
    CONSTRAINT fk_collab_message_posting_message FOREIGN KEY (message_id) REFERENCES collaboration_message (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_message_posting_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '채팅방에 공유된 사용자 공고';

-- =====================================================================
--  결제 / AI 사용량
-- =====================================================================
CREATE TABLE IF NOT EXISTS payment (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    provider      VARCHAR(20) NULL,
    product_type  VARCHAR(30) NOT NULL DEFAULT 'CREDIT',
    product_code  VARCHAR(50) NULL,
    order_id      VARCHAR(100) NULL,
    payment_key   VARCHAR(200) NULL,
    amount        INT NOT NULL,
    plan          VARCHAR(20) NULL,
    credit_amount INT NULL,
    policy_snapshot_json JSON NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING/PAID/FAILED/REFUNDED
    paid_at       DATETIME NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_id (order_id),
    UNIQUE KEY uk_payment_payment_key (payment_key),
    KEY idx_payment_user (user_id),
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS subscription_plan (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    code          VARCHAR(30) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    monthly_price INT NOT NULL DEFAULT 0,
    yearly_price  INT NULL,
    description   VARCHAR(500) NULL,
    active        TINYINT(1) NOT NULL DEFAULT 1,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_plan_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS credit_product (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    code          VARCHAR(50) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    price         INT NOT NULL,
    credit_amount INT NOT NULL,
    description   VARCHAR(500) NULL,
    badge         VARCHAR(50) NULL,
    enabled       TINYINT(1) NOT NULL DEFAULT 1,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_credit_product_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS benefit_catalog (
    code        VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    active      TINYINT(1) NOT NULL DEFAULT 1,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS billing_product (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    code         VARCHAR(50) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    name         VARCHAR(100) NOT NULL,
    price        INT NOT NULL,
    description  VARCHAR(500) NULL,
    badge        VARCHAR(50) NULL,
    active       TINYINT(1) NOT NULL DEFAULT 1,
    sort_order   INT NOT NULL DEFAULT 0,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_product_code (code),
    KEY idx_billing_product_type (product_type, active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS billing_product_benefit (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    product_code  VARCHAR(50) NOT NULL,
    benefit_code  VARCHAR(50) NOT NULL,
    quantity      INT NOT NULL,
    validity_days INT NOT NULL DEFAULT 30,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_product_benefit (product_code, benefit_code),
    KEY idx_billing_product_benefit_code (benefit_code),
    CONSTRAINT fk_billing_product_benefit_product FOREIGN KEY (product_code) REFERENCES billing_product (code) ON DELETE CASCADE,
    CONSTRAINT fk_billing_product_benefit_catalog FOREIGN KEY (benefit_code) REFERENCES benefit_catalog (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS subscription_benefit_policy (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    plan_code      VARCHAR(30) NOT NULL,
    benefit_code   VARCHAR(50) NOT NULL,
    benefit_name   VARCHAR(100) NOT NULL,
    benefit_type   VARCHAR(30) NOT NULL DEFAULT 'TICKET',
    quantity       INT NOT NULL DEFAULT 0,
    reset_cycle    VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    overage_policy VARCHAR(20) NOT NULL DEFAULT 'BLOCK',
    credit_cost    INT NOT NULL DEFAULT 0,
    active         TINYINT(1) NOT NULL DEFAULT 1,
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_benefit_policy (plan_code, benefit_code),
    KEY idx_subscription_benefit_policy_plan (plan_code),
    KEY idx_subscription_benefit_policy_benefit (benefit_code),
    CONSTRAINT fk_subscription_benefit_policy_plan FOREIGN KEY (plan_code) REFERENCES subscription_plan (code) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_subscription (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    payment_id           BIGINT NULL,
    user_id              BIGINT NOT NULL,
    plan_code            VARCHAR(30) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    current_period_start DATETIME NOT NULL,
    current_period_end   DATETIME NOT NULL,
    policy_snapshot_json JSON NULL,
    canceled_at          DATETIME NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_subscription_payment (payment_id),
    KEY idx_user_subscription_user_status (user_id, status),
    KEY idx_user_subscription_plan (plan_code),
    CONSTRAINT fk_user_subscription_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_subscription_payment FOREIGN KEY (payment_id) REFERENCES payment (id) ON DELETE SET NULL,
    CONSTRAINT fk_user_subscription_plan FOREIGN KEY (plan_code) REFERENCES subscription_plan (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS billing_policy_change (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    target_type           VARCHAR(40) NOT NULL,
    target_code           VARCHAR(120) NOT NULL,
    current_snapshot_json JSON NULL,
    next_snapshot_json    JSON NOT NULL,
    effective_from        DATETIME NOT NULL,
    apply_mode            VARCHAR(40) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by            BIGINT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    canceled_by           BIGINT NULL,
    canceled_at           DATETIME NULL,
    applied_at            DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_billing_policy_change_target (target_type, target_code, status, effective_from),
    KEY idx_billing_policy_change_status (status, effective_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_benefit_balance (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    benefit_code       VARCHAR(50) NOT NULL,
    period_start       DATETIME NOT NULL,
    period_end         DATETIME NOT NULL,
    granted_quantity   INT NOT NULL DEFAULT 0,
    used_quantity      INT NOT NULL DEFAULT 0,
    remaining_quantity INT NOT NULL DEFAULT 0,
    source_plan_code   VARCHAR(30) NULL,
    source_type        VARCHAR(30) NOT NULL DEFAULT 'PLAN',
    source_code        VARCHAR(50) NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_benefit_balance_period (user_id, benefit_code, period_start),
    KEY idx_user_benefit_balance_user_period (user_id, period_start, period_end),
    KEY idx_user_benefit_balance_benefit (benefit_code),
    CONSTRAINT fk_user_benefit_balance_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_benefit_balance_plan FOREIGN KEY (source_plan_code) REFERENCES subscription_plan (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_feature_benefit_policy (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    feature_type        VARCHAR(80) NOT NULL,
    benefit_code        VARCHAR(50) NOT NULL,
    charge_unit         VARCHAR(30) NOT NULL,
    included_in_ticket  TINYINT(1) NOT NULL DEFAULT 1,
    default_credit_cost INT NOT NULL DEFAULT 0,
    min_credit_cost     INT NOT NULL DEFAULT 0,
    max_credit_cost     INT NOT NULL DEFAULT 0,
    credit_unit_tokens  INT NOT NULL DEFAULT 1000,
    active              TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_feature_benefit_policy_feature (feature_type),
    KEY idx_ai_feature_benefit_policy_benefit (benefit_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_usage_log (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    user_id             BIGINT NOT NULL,
    application_case_id BIGINT NULL,
    feature_type        VARCHAR(80) NOT NULL,               -- JOB_ANALYSIS/COMPANY_RESEARCH/QUESTION_GEN/INTERVIEW/...
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

CREATE TABLE IF NOT EXISTS ai_runtime_setting (
    setting_key VARCHAR(80) NOT NULL,
    value_json  JSON NOT NULL,
    updated_by  BIGINT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key),
    CONSTRAINT fk_ai_runtime_setting_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS benefit_transaction (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    benefit_code     VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount           INT NOT NULL,
    balance_after    INT NOT NULL,
    ref_type         VARCHAR(40) NULL,
    ref_id           BIGINT NULL,
    ai_usage_log_id  BIGINT NULL,
    reason           VARCHAR(255) NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_benefit_consume_ref (benefit_code, transaction_type, ref_type, ref_id),
    KEY idx_benefit_transaction_user (user_id),
    KEY idx_benefit_transaction_benefit (benefit_code),
    KEY idx_benefit_transaction_ai_usage (ai_usage_log_id),
    CONSTRAINT fk_benefit_transaction_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_benefit_transaction_ai_usage FOREIGN KEY (ai_usage_log_id) REFERENCES ai_usage_log (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT IGNORE INTO subscription_plan
    (code, name, monthly_price, yearly_price, description, active, sort_order)
VALUES
    ('FREE', '무료', 0, 0, '기본 체험 플랜', 1, 10),
    ('BASIC', '베이직', 9900, 7900, '가벼운 취업 준비 플랜', 1, 20),
    ('PRO', '프로', 29000, 23000, '실전 취업 준비 플랜', 1, 30),
    ('PREMIUM', '프리미엄', 49000, 39000, '고급 면접 패키지 플랜', 1, 40);

INSERT INTO credit_product
    (code, name, price, credit_amount, description, badge, enabled, sort_order)
VALUES
    ('CREDIT_10', '크레딧 10개', 4900, 10, 'AI 기능 추가 이용용 크레딧', NULL, 1, 10),
    ('CREDIT_30', '크레딧 30개', 12900, 30, '자주 쓰는 사용자를 위한 크레딧 묶음', '인기', 1, 20),
    ('CREDIT_100', '크레딧 100개', 39000, 100, '팀 프로젝트와 시연용 대용량 크레딧', '최대 할인', 1, 30)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    credit_amount = VALUES(credit_amount),
    description = VALUES(description),
    badge = VALUES(badge),
    enabled = VALUES(enabled),
    sort_order = VALUES(sort_order);

INSERT INTO benefit_catalog
    (code, name, description, active, sort_order)
VALUES
    ('APPLICATION_ANALYSIS', '지원건 분석권', '공고, 기업, 적합도 등 지원 건 기반 AI 분석 사용권', 1, 10),
    ('MOCK_INTERVIEW', '모의면접권', '질문 생성, 답변 평가, 리포트 등 텍스트 면접 사용권', 1, 20),
    ('VOICE_INTERVIEW', '음성면접권', '음성 면접과 음성 답변 채점 사용권', 1, 30),
    ('VIDEO_ANALYSIS', '영상분석권', '영상/비언어 면접 분석 사용권', 1, 40),
    ('AVATAR_INTERVIEW', '아바타면접권', '아바타 면접관 세션 사용권', 1, 50),
    ('CORRECTION', 'AI 첨삭권', '면접 답변, 자기소개서, 이력서, 포트폴리오 첨삭 사용권', 1, 60),
    ('PROFILE_AI', '프로필 AI권', '프로필 요약, 기술 추출, 완성도 진단 사용권', 1, 70),
    ('CAREER_STRATEGY', '커리어 전략권', '부족 역량, 로드맵, 장기 경향, 대시보드 인사이트 사용권', 1, 80),
    ('COMMUNITY_AI', '커뮤니티 AI권', '면접 후기 요약, 태그 추천, 실제 질문 추출 사용권', 1, 90)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    active = VALUES(active),
    sort_order = VALUES(sort_order);

INSERT INTO billing_product
    (code, product_type, name, price, description, badge, active, sort_order)
VALUES
    ('PACK_APPLICATION_20', 'BENEFIT_PACK', '지원건 분석권 20장', 9900, '구독 포함량 소진 후 추가 분석용 사용권', NULL, 1, 110),
    ('PACK_INTERVIEW_10', 'BENEFIT_PACK', '모의면접권 10장', 9900, '질문 생성·평가·리포트 추가 이용권', NULL, 1, 120),
    ('PACK_CORRECTION_10', 'BENEFIT_PACK', 'AI 첨삭권 10장', 7900, '자기소개서·답변·이력서 첨삭 추가 이용권', NULL, 1, 130),
    ('PACK_VOICE_5', 'BENEFIT_PACK', '음성면접권 5장', 9900, '음성 면접 추가 이용권', NULL, 1, 140),
    ('PACK_VIDEO_3', 'BENEFIT_PACK', '영상분석권 3장', 12900, '영상/비언어 분석 추가 이용권', NULL, 1, 150),
    ('PACK_AVATAR_3', 'BENEFIT_PACK', '아바타면접권 3장', 14900, '아바타 면접관 추가 이용권', NULL, 1, 160)
ON DUPLICATE KEY UPDATE
    product_type = VALUES(product_type),
    name = VALUES(name),
    price = VALUES(price),
    description = VALUES(description),
    badge = VALUES(badge),
    active = VALUES(active),
    sort_order = VALUES(sort_order);

INSERT INTO billing_product_benefit
    (product_code, benefit_code, quantity, validity_days, sort_order)
VALUES
    ('PACK_APPLICATION_20', 'APPLICATION_ANALYSIS', 20, 30, 10),
    ('PACK_INTERVIEW_10', 'MOCK_INTERVIEW', 10, 30, 10),
    ('PACK_CORRECTION_10', 'CORRECTION', 10, 30, 10),
    ('PACK_VOICE_5', 'VOICE_INTERVIEW', 5, 30, 10),
    ('PACK_VIDEO_3', 'VIDEO_ANALYSIS', 3, 30, 10),
    ('PACK_AVATAR_3', 'AVATAR_INTERVIEW', 3, 30, 10)
ON DUPLICATE KEY UPDATE
    quantity = VALUES(quantity),
    validity_days = VALUES(validity_days),
    sort_order = VALUES(sort_order);

INSERT IGNORE INTO subscription_benefit_policy
    (plan_code, benefit_code, benefit_name, benefit_type, quantity, reset_cycle, overage_policy, credit_cost, active, sort_order)
VALUES
    ('FREE', 'APPLICATION_ANALYSIS', '지원건 분석권', 'TICKET', 3, 'MONTHLY', 'BLOCK', 0, 1, 10),
    ('FREE', 'MOCK_INTERVIEW', '모의면접권', 'TICKET', 1, 'MONTHLY', 'BLOCK', 0, 1, 20),
    ('FREE', 'VOICE_INTERVIEW', '음성면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 30),
    ('FREE', 'VIDEO_ANALYSIS', '영상분석권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 40),
    ('FREE', 'AVATAR_INTERVIEW', '아바타면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 50),
    ('BASIC', 'APPLICATION_ANALYSIS', '지원건 분석권', 'TICKET', 20, 'MONTHLY', 'BLOCK', 0, 1, 10),
    ('BASIC', 'MOCK_INTERVIEW', '모의면접권', 'TICKET', 10, 'MONTHLY', 'BLOCK', 0, 1, 20),
    ('BASIC', 'VOICE_INTERVIEW', '음성면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 30),
    ('BASIC', 'VIDEO_ANALYSIS', '영상분석권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 40),
    ('BASIC', 'AVATAR_INTERVIEW', '아바타면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 50),
    ('PRO', 'APPLICATION_ANALYSIS', '지원건 분석권', 'TICKET', 60, 'MONTHLY', 'BLOCK', 0, 1, 10),
    ('PRO', 'MOCK_INTERVIEW', '모의면접권', 'TICKET', 30, 'MONTHLY', 'BLOCK', 0, 1, 20),
    ('PRO', 'VOICE_INTERVIEW', '음성면접권', 'TICKET', 5, 'MONTHLY', 'BLOCK', 0, 1, 30),
    ('PRO', 'VIDEO_ANALYSIS', '영상분석권', 'TICKET', 1, 'MONTHLY', 'UPGRADE', 0, 1, 40),
    ('PRO', 'AVATAR_INTERVIEW', '아바타면접권', 'TICKET', 0, 'MONTHLY', 'UPGRADE', 0, 1, 50),
    ('PREMIUM', 'APPLICATION_ANALYSIS', '지원건 분석권', 'TICKET', 150, 'MONTHLY', 'BLOCK', 0, 1, 10),
    ('PREMIUM', 'MOCK_INTERVIEW', '모의면접권', 'TICKET', 60, 'MONTHLY', 'BLOCK', 0, 1, 20),
    ('PREMIUM', 'VOICE_INTERVIEW', '음성면접권', 'TICKET', 15, 'MONTHLY', 'BLOCK', 0, 1, 30),
    ('PREMIUM', 'VIDEO_ANALYSIS', '영상분석권', 'TICKET', 5, 'MONTHLY', 'BLOCK', 0, 1, 40),
    ('PREMIUM', 'AVATAR_INTERVIEW', '아바타면접권', 'TICKET', 5, 'MONTHLY', 'BLOCK', 0, 1, 50);

INSERT INTO subscription_benefit_policy
    (plan_code, benefit_code, benefit_name, benefit_type, quantity, reset_cycle, overage_policy, credit_cost, active, sort_order)
VALUES
    ('FREE', 'CORRECTION', 'AI 첨삭권', 'TICKET', 1, 'MONTHLY', 'CREDIT', 2, 1, 60),
    ('FREE', 'PROFILE_AI', '프로필 AI권', 'TICKET', 3, 'MONTHLY', 'CREDIT', 1, 1, 70),
    ('FREE', 'CAREER_STRATEGY', '커리어 전략권', 'TICKET', 2, 'MONTHLY', 'CREDIT', 2, 1, 80),
    ('FREE', 'COMMUNITY_AI', '커뮤니티 AI권', 'TICKET', 5, 'MONTHLY', 'CREDIT', 1, 1, 90),
    ('BASIC', 'CORRECTION', 'AI 첨삭권', 'TICKET', 10, 'MONTHLY', 'CREDIT', 2, 1, 60),
    ('BASIC', 'PROFILE_AI', '프로필 AI권', 'TICKET', 20, 'MONTHLY', 'CREDIT', 1, 1, 70),
    ('BASIC', 'CAREER_STRATEGY', '커리어 전략권', 'TICKET', 15, 'MONTHLY', 'CREDIT', 2, 1, 80),
    ('BASIC', 'COMMUNITY_AI', '커뮤니티 AI권', 'TICKET', 30, 'MONTHLY', 'CREDIT', 1, 1, 90),
    ('PRO', 'CORRECTION', 'AI 첨삭권', 'TICKET', 30, 'MONTHLY', 'CREDIT', 2, 1, 60),
    ('PRO', 'PROFILE_AI', '프로필 AI권', 'TICKET', 60, 'MONTHLY', 'CREDIT', 1, 1, 70),
    ('PRO', 'CAREER_STRATEGY', '커리어 전략권', 'TICKET', 60, 'MONTHLY', 'CREDIT', 2, 1, 80),
    ('PRO', 'COMMUNITY_AI', '커뮤니티 AI권', 'TICKET', 100, 'MONTHLY', 'CREDIT', 1, 1, 90),
    ('PREMIUM', 'CORRECTION', 'AI 첨삭권', 'TICKET', 60, 'MONTHLY', 'CREDIT', 2, 1, 60),
    ('PREMIUM', 'PROFILE_AI', '프로필 AI권', 'TICKET', 150, 'MONTHLY', 'CREDIT', 1, 1, 70),
    ('PREMIUM', 'CAREER_STRATEGY', '커리어 전략권', 'TICKET', 100, 'MONTHLY', 'CREDIT', 2, 1, 80),
    ('PREMIUM', 'COMMUNITY_AI', '커뮤니티 AI권', 'TICKET', 200, 'MONTHLY', 'CREDIT', 1, 1, 90)
ON DUPLICATE KEY UPDATE
    benefit_name = VALUES(benefit_name),
    benefit_type = VALUES(benefit_type),
    quantity = VALUES(quantity),
    reset_cycle = VALUES(reset_cycle),
    overage_policy = VALUES(overage_policy),
    credit_cost = VALUES(credit_cost),
    active = VALUES(active),
    sort_order = VALUES(sort_order);

UPDATE subscription_benefit_policy
   SET overage_policy = 'CREDIT',
       credit_cost = CASE benefit_code
           WHEN 'APPLICATION_ANALYSIS' THEN 2
           WHEN 'MOCK_INTERVIEW' THEN 2
           WHEN 'VOICE_INTERVIEW' THEN 3
           WHEN 'VIDEO_ANALYSIS' THEN 5
           WHEN 'AVATAR_INTERVIEW' THEN 6
           ELSE credit_cost
       END
 WHERE quantity > 0
   AND benefit_code IN ('APPLICATION_ANALYSIS', 'MOCK_INTERVIEW', 'VOICE_INTERVIEW', 'VIDEO_ANALYSIS', 'AVATAR_INTERVIEW');

INSERT IGNORE INTO ai_feature_benefit_policy
    (feature_type, benefit_code, charge_unit, included_in_ticket, default_credit_cost, active)
VALUES
    ('JOB_POSTING_OCR', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 0, 1),
    ('JOB_ANALYSIS', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 0, 1),
    ('COMPANY_RESEARCH', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 0, 1),
    ('FIT_ANALYSIS', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 0, 1),
    ('INTERVIEW_QUESTION_GEN', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_FOLLOWUP_GEN', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_ANSWER_EVAL', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_CRITIC', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_REPORT', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_PLANNER', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_MODEL_ANSWER', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_VOICE_SESSION', 'VOICE_INTERVIEW', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_VIDEO_ANALYSIS', 'VIDEO_ANALYSIS', 'PER_SESSION', 1, 0, 1),
    ('INTERVIEW_AVATAR_SESSION', 'AVATAR_INTERVIEW', 'PER_SESSION', 1, 0, 1);

INSERT INTO ai_feature_benefit_policy
    (feature_type, benefit_code, charge_unit, included_in_ticket, default_credit_cost, active)
VALUES
    ('PROFILE_SUMMARY', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('PROFILE_SKILL_EXTRACT', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('PROFILE_SELF_INTRO_KEYWORD', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('PROFILE_CAREER_KEYWORD', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('PROFILE_COMPLETENESS', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('JOB_POSTING_METADATA', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('JOB_REQUIRED_CONDITION', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('JOB_PREFERRED_CONDITION', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('JOB_DUTY_SUMMARY', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('INTERVIEW_POINT_EXTRACTION', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('GAP_ROADMAP_RECOMMENDATION', 'CAREER_STRATEGY', 'PER_CASE', 1, 2, 1),
    ('CERTIFICATION_RECOMMENDATION', 'CAREER_STRATEGY', 'PER_CASE', 1, 2, 1),
    ('CAREER_TREND_ANALYSIS', 'CAREER_STRATEGY', 'PER_REQUEST', 1, 2, 1),
    ('NEXT_APPLICATION_RECOMMENDATION', 'CAREER_STRATEGY', 'PER_REQUEST', 1, 2, 1),
    ('DASHBOARD_INSIGHT', 'CAREER_STRATEGY', 'PER_REQUEST', 1, 2, 1),
    ('INTERVIEW_DIALOGUE', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 2, 1),
    ('INTERVIEW_VOICE_SCORING', 'VOICE_INTERVIEW', 'PER_SESSION', 1, 3, 1),
    ('CORRECTION_INTERVIEW_ANSWER', 'CORRECTION', 'PER_REQUEST', 1, 2, 1),
    ('CORRECTION_SELF_INTRO', 'CORRECTION', 'PER_REQUEST', 1, 2, 1),
    ('CORRECTION_RESUME', 'CORRECTION', 'PER_REQUEST', 1, 2, 1),
    ('CORRECTION_PORTFOLIO', 'CORRECTION', 'PER_REQUEST', 1, 2, 1),
    ('USAGE_PLAN_RECOMMENDATION', 'CAREER_STRATEGY', 'PER_REQUEST', 1, 1, 1),
    ('COMMUNITY_INTERVIEW_SUMMARY', 'COMMUNITY_AI', 'PER_POST', 1, 1, 1),
    ('COMMUNITY_AUTO_TAGGING', 'COMMUNITY_AI', 'PER_POST', 1, 1, 1),
    ('COMMUNITY_INTERVIEW_QUESTION_EXTRACTION', 'COMMUNITY_AI', 'PER_POST', 1, 1, 1),
    ('COMMUNITY_POST_RECOMMENDATION', 'COMMUNITY_AI', 'PER_REQUEST', 1, 1, 1)
ON DUPLICATE KEY UPDATE
    benefit_code = VALUES(benefit_code),
    charge_unit = VALUES(charge_unit),
    included_in_ticket = VALUES(included_in_ticket),
    default_credit_cost = VALUES(default_credit_cost),
    active = VALUES(active);

UPDATE ai_feature_benefit_policy
   SET default_credit_cost = CASE benefit_code
       WHEN 'APPLICATION_ANALYSIS' THEN 2
       WHEN 'MOCK_INTERVIEW' THEN 2
       WHEN 'VOICE_INTERVIEW' THEN 3
       WHEN 'VIDEO_ANALYSIS' THEN 5
       WHEN 'AVATAR_INTERVIEW' THEN 6
       WHEN 'CORRECTION' THEN 2
       WHEN 'PROFILE_AI' THEN 1
       WHEN 'CAREER_STRATEGY' THEN 2
       WHEN 'COMMUNITY_AI' THEN 1
       ELSE default_credit_cost
   END
 WHERE active = 1;

-- 실제 사용량 표본의 P95를 상한으로 둔 기능별 크레딧 범위.
-- 증분 DB에는 db/patches/20260706e_ai_usage_credit_range.sql을 적용한다.
INSERT INTO ai_feature_benefit_policy
    (feature_type, benefit_code, charge_unit, included_in_ticket, default_credit_cost,
     min_credit_cost, max_credit_cost, credit_unit_tokens, active)
VALUES
    ('JOB_ANALYSIS', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 1, 1, 5, 1000, 1),
    ('COMPANY_RESEARCH', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 1, 1, 6, 1000, 1),
    ('JOB_POSTING_METADATA', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 3, 3, 6, 1000, 1),
    ('JOB_POSTING_OCR', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 1, 1, 12, 2000, 1),
    ('FIT_ANALYSIS', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 1, 1, 7, 1000, 1),
    ('DASHBOARD_SUMMARY', 'CAREER_STRATEGY', 'PER_REQUEST', 0, 1, 1, 2, 1000, 1),
    ('CAREER_TREND', 'CAREER_STRATEGY', 'PER_REQUEST', 0, 1, 1, 6, 1000, 1),
    ('LONG_TERM_ANALYSIS', 'CAREER_STRATEGY', 'PER_REQUEST', 0, 2, 2, 4, 1000, 1),
    ('INTERVIEW_QUESTION_GEN', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 4, 1000, 1),
    ('INTERVIEW_FOLLOWUP_GEN', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 2, 1000, 1),
    ('INTERVIEW_ANSWER_EVAL', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 4, 1000, 1),
    ('INTERVIEW_CRITIC', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 2, 1000, 1),
    ('INTERVIEW_MODEL_ANSWER', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 5, 1000, 1),
    ('INTERVIEW_REPORT', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 3, 1000, 1),
    ('INTERVIEW_VOICE_SCORING', 'VOICE_INTERVIEW', 'PER_SESSION', 1, 2, 2, 3, 1000, 1),
    ('CORRECTION_INTERVIEW_ANSWER', 'CORRECTION', 'PER_REQUEST', 0, 2, 2, 5, 1000, 1),
    ('CORRECTION_SELF_INTRO', 'CORRECTION', 'PER_REQUEST', 0, 2, 2, 5, 1000, 1),
    ('CORRECTION_RESUME', 'CORRECTION', 'PER_REQUEST', 0, 2, 2, 5, 1000, 1),
    ('CORRECTION_PORTFOLIO', 'CORRECTION', 'PER_REQUEST', 0, 2, 2, 5, 1000, 1),
    ('PROFILE_COMPLETENESS', 'PROFILE_AI', 'PER_REQUEST', 0, 0, 0, 6, 1000, 1),
    ('PROFILE_SUMMARY', 'PROFILE_AI', 'PER_REQUEST', 0, 0, 0, 2, 1000, 1),
    ('PROFILE_SKILL_EXTRACT', 'PROFILE_AI', 'PER_REQUEST', 0, 0, 0, 0, 1000, 1)
ON DUPLICATE KEY UPDATE
    benefit_code = VALUES(benefit_code), charge_unit = VALUES(charge_unit),
    included_in_ticket = VALUES(included_in_ticket), default_credit_cost = VALUES(default_credit_cost),
    min_credit_cost = VALUES(min_credit_cost), max_credit_cost = VALUES(max_credit_cost),
    credit_unit_tokens = VALUES(credit_unit_tokens), active = VALUES(active);

CREATE TABLE IF NOT EXISTS credit_transaction (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    ai_usage_log_id BIGINT NULL,
    type            VARCHAR(30) NOT NULL,                      -- AI_USAGE/CHARGE/REFUND/ADMIN_ADJUST
    amount          INT NOT NULL,                              -- charge/refund are positive, AI usage is negative
    balance_after   INT NOT NULL,
    feature_type    VARCHAR(80) NULL,
    reason          VARCHAR(255) NULL,
    request_key     VARCHAR(120) NULL COMMENT '클라이언트 재시도 중복 반영 방지 키',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_credit_transaction_ai_usage_type (ai_usage_log_id, type),
    UNIQUE KEY uq_credit_transaction_user_type_request (user_id, type, request_key),
    KEY idx_credit_transaction_user (user_id),
    CONSTRAINT fk_credit_transaction_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_transaction_ai_usage FOREIGN KEY (ai_usage_log_id) REFERENCES ai_usage_log (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS correction_request (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    user_id             BIGINT NOT NULL,
    request_key         VARCHAR(120) NULL,
    application_case_id BIGINT NULL,
    correction_type     VARCHAR(40) NOT NULL,
    source_type         VARCHAR(40) NOT NULL DEFAULT 'DIRECT_INPUT',
    source_ref_id       BIGINT NULL,
    original_text       MEDIUMTEXT NOT NULL,
    improved_text       MEDIUMTEXT NULL,
    result_json         JSON NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    ai_usage_log_id     BIGINT NULL,
    admin_memo          VARCHAR(2000) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_correction_request_user_key (user_id, request_key),
    KEY idx_correction_request_user (user_id),
    KEY idx_correction_request_case (application_case_id),
    KEY idx_correction_request_type (correction_type),
    KEY idx_correction_request_ai_usage (ai_usage_log_id),
    CONSTRAINT fk_correction_request_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_correction_request_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE SET NULL,
    CONSTRAINT fk_correction_request_ai_usage FOREIGN KEY (ai_usage_log_id) REFERENCES ai_usage_log (id) ON DELETE SET NULL
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
    nickname_profile_id BIGINT   NULL COMMENT '작성 시 선택한 표시용 닉네임 프로필(user_nickname_profile.id). NULL=계정 기본/계정명 표시',
    view_count     INT          NOT NULL DEFAULT 0,
    comment_count  INT          NOT NULL DEFAULT 0,
    like_count     INT          NOT NULL DEFAULT 0,
    dislike_count      INT      NOT NULL DEFAULT 0 COMMENT '싫어요 수(개인화용 축)',
    report_count       INT      NOT NULL DEFAULT 0 COMMENT '누적 신고 수(임계 이상 시 비작성자에게 자동 블러)',
    recommend_count    INT      NOT NULL DEFAULT 0 COMMENT '추천 수(트렌드·인기글용 축)',
    disrecommend_count INT      NOT NULL DEFAULT 0 COMMENT '비추천 수',
    bookmark_count INT          NOT NULL DEFAULT 0,
    scrap_count    INT          NOT NULL DEFAULT 0 COMMENT '스크랩 수(post_scrap 집계 캐시)',
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
    review_action VARCHAR(10)  NULL COMMENT '경계 검열 결과 수동 결정(HIDE/KEEP)',
    reviewed_by   BIGINT       NULL COMMENT '경계 검열 결과를 결정한 관리자 ID',
    reviewed_at   DATETIME     NULL COMMENT '경계 검열 결과 수동 검토 시각',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at  DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_ai_result_task (post_id, task_type),
    KEY idx_post_ai_result_status (task_type, status, completed_at),
    KEY idx_post_ai_result_review (task_type, status, review_action, completed_at),
    CONSTRAINT fk_post_ai_result_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_moderation_setting (
    id             TINYINT      NOT NULL,
    strictness     VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    hide_threshold DECIMAL(3,2) NOT NULL DEFAULT 0.80,
    sanction_threshold          INT NOT NULL DEFAULT 3  COMMENT '자동 차단 트리거 누적 숨김 글 수(patch 20260619_f)',
    block_days                  INT NOT NULL DEFAULT 7  COMMENT '자동 차단 유지 일수(patch 20260619_f)',
    report_blur_threshold       INT NOT NULL DEFAULT 3  COMMENT '신고 누적 자동 블러 임계(patch 20260706b)',
    post_rate_window_seconds    INT NOT NULL DEFAULT 60 COMMENT '게시글 rate-limit 윈도(초)',
    post_rate_max               INT NOT NULL DEFAULT 10 COMMENT '윈도 내 허용 게시글 수(0=비활성)',
    comment_rate_window_seconds INT NOT NULL DEFAULT 60 COMMENT '댓글 rate-limit 윈도(초)',
    comment_rate_max            INT NOT NULL DEFAULT 20 COMMENT '윈도 내 허용 댓글 수(0=비활성)',
    inquiry_rate_window_seconds INT NOT NULL DEFAULT 600 COMMENT '문의 rate-limit 윈도(초)',
    inquiry_rate_max            INT NOT NULL DEFAULT 0  COMMENT '윈도 내 허용 문의 수(0=무제약=기존 동작)',
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
    mention_user_id BIGINT    NULL,                         -- 답글 멘션 대상 사용자 (patch 20260619_f 동기화)
    content      MEDIUMTEXT   NOT NULL,
    is_anonymous TINYINT(1)   NOT NULL DEFAULT 1,
    nickname_profile_id BIGINT NULL COMMENT '작성 시 선택한 표시용 닉네임 프로필(user_nickname_profile.id). NULL=계정 기본/계정명 표시',
    status       VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    like_count   INT          NOT NULL DEFAULT 0,
    dislike_count      INT    NOT NULL DEFAULT 0 COMMENT '싫어요 수',
    recommend_count    INT    NOT NULL DEFAULT 0 COMMENT '추천 수',
    disrecommend_count INT    NOT NULL DEFAULT 0 COMMENT '비추천 수',
    report_count       INT    NOT NULL DEFAULT 0 COMMENT '누적 신고 수(임계 이상 시 비작성자 블러, patch 20260706e)',
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
    axis           VARCHAR(20)  NOT NULL DEFAULT 'PREFERENCE' COMMENT '리액션 축(RECOMMEND_AXIS/PREFERENCE/BOOKMARK)',
    is_anonymous   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '익명 리액션 — 타인 시점 목록 제외, 집계 포함',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_reaction_axis (user_id, post_id, axis),
    KEY idx_pr_post (post_id),
    CONSTRAINT fk_pr_user FOREIGN KEY (user_id) REFERENCES users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_pr_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- 댓글 반응(같은 축에서 반대 클릭 시 교체, 재클릭 시 취소)
CREATE TABLE IF NOT EXISTS comment_reaction (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    comment_id     BIGINT       NOT NULL,
    reaction_type  VARCHAR(20)  NOT NULL,
    axis           VARCHAR(20)  NOT NULL DEFAULT 'PREFERENCE' COMMENT '리액션 축(RECOMMEND_AXIS/PREFERENCE)',
    is_anonymous   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '익명 리액션 — 타인 시점 목록 제외, 집계 포함',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_reaction_axis (user_id, comment_id, axis),
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
    deleted_at    DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_guideline_status (status, published_at DESC),
    KEY idx_guideline_deleted (deleted_at),
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

-- 문의테이블
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
    scheduled_at  DATETIME     NULL,                        -- 예약 발행 시각 (patch 20260701_f 동기화)
    deleted_at    DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notice_list (status, is_pinned DESC, published_at DESC),
    KEY idx_notice_deleted (deleted_at),
    CONSTRAINT fk_notice_admin FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE SET NULL
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 환불 정책은 게시된 버전을 덮어쓰지 않고 버전 행을 계속 추가한다.
-- 현재 시행 정책은 PUBLISHED + effective_at <= NOW() 중 최신 버전으로 계산한다.
CREATE TABLE IF NOT EXISTS refund_policy (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    policy_code    VARCHAR(50)  NOT NULL DEFAULT 'REFUND_DEFAULT',
    version        INT          NOT NULL,
    title          VARCHAR(255) NOT NULL,
    summary        VARCHAR(500) NULL,
    content        MEDIUMTEXT   NOT NULL,
    rules_json     JSON         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    is_adverse     TINYINT(1)   NOT NULL DEFAULT 0,
    effective_at   DATETIME     NULL,
    published_at   DATETIME     NULL,
    notice_id      BIGINT       NULL,
    created_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    draft_slot     TINYINT GENERATED ALWAYS AS
        (CASE WHEN status = 'DRAFT' THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_policy_code_version (policy_code, version),
    UNIQUE KEY uk_refund_policy_single_draft (policy_code, draft_slot),
    KEY idx_refund_policy_current (policy_code, status, effective_at, version),
    KEY idx_refund_policy_notice (notice_id),
    CONSTRAINT fk_refund_policy_notice FOREIGN KEY (notice_id) REFERENCES notice (id) ON DELETE SET NULL,
    CONSTRAINT fk_refund_policy_admin FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_refund_policy_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 정책을 실제로 고지한 버전과 시점을 남긴다. NOTICE는 토스트 노출,
-- PAYMENT/CREDIT_USE/BENEFIT_USE는 각 행위 전 정책 확인 기록이다.
CREATE TABLE IF NOT EXISTS refund_policy_acknowledgement (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    refund_policy_id BIGINT      NOT NULL,
    trigger_type     VARCHAR(30) NOT NULL,
    action_key       VARCHAR(120) NOT NULL DEFAULT 'GLOBAL',
    acknowledged_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_policy_ack (user_id, refund_policy_id, trigger_type, action_key),
    KEY idx_refund_policy_ack_policy (refund_policy_id, trigger_type),
    CONSTRAINT fk_refund_policy_ack_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refund_policy_ack_policy FOREIGN KEY (refund_policy_id) REFERENCES refund_policy (id) ON DELETE CASCADE,
    CONSTRAINT chk_refund_policy_ack_trigger CHECK
        (trigger_type IN ('NOTICE', 'PAYMENT', 'CREDIT_USE', 'BENEFIT_USE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 가결제 단계의 환불 신청과 관리자 최종 판정을 보관한다.
-- 실제 PG 취소나 부분 환불은 수행하지 않고 승인 시 payment 상태만 REFUNDED 로 변경한다.
CREATE TABLE IF NOT EXISTS refund_request (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    payment_id          BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'REQUESTED',
    reason_code         VARCHAR(40)   NOT NULL,
    reason_text         VARCHAR(1000) NULL,
    eligibility_result  VARCHAR(30)   NOT NULL,
    credit_used         TINYINT(1)    NOT NULL DEFAULT 0,
    benefit_used        TINYINT(1)    NOT NULL DEFAULT 0,
    refund_amount       INT           NOT NULL,
    decision_basis_json JSON          NOT NULL,
    reviewed_by         BIGINT        NULL,
    reviewed_reason     VARCHAR(1000) NULL,
    requested_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at         DATETIME      NULL,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_request_payment (payment_id),
    KEY idx_refund_request_user (user_id, requested_at),
    KEY idx_refund_request_status (status, requested_at),
    CONSTRAINT fk_refund_request_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_refund_request_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refund_request_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_refund_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_refund_request_eligibility CHECK
        (eligibility_result IN ('ELIGIBLE', 'INELIGIBLE', 'REVIEW_REQUIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT IGNORE INTO refund_policy
    (policy_code, version, title, summary, content, rules_json, status, is_adverse,
     effective_at, published_at, created_by)
VALUES
    ('REFUND_DEFAULT', 1, '환불 정책',
     '전자상거래 관련 법령과 서비스 운영 기준에 따른 기본 환불 정책입니다.',
     '결제 후 7일 이내이며 유료 기능을 사용하지 않은 경우 전액 환불을 신청할 수 있습니다. 크레딧 또는 사용권을 사용한 결제 건과 중복 결제, 시스템 오류 등 예외 사유는 운영자가 결제 및 사용 이력을 확인한 뒤 처리합니다.',
     JSON_OBJECT(
         'legalBasis', 'E_COMMERCE_ACT',
         'withdrawalDays', 7,
         'unusedPolicy', 'FULL_REFUND',
         'usedPolicy', 'NO_REFUND',
         'exceptionCodes', JSON_ARRAY('DUPLICATE_PAYMENT', 'SYSTEM_ERROR', 'LEGAL_REQUIREMENT'),
         'noticeScopes', JSON_ARRAY('PAYMENT', 'CREDIT_USE', 'BENEFIT_USE')
     ),
     'PUBLISHED', 0, '2026-01-01 00:00:00', CURRENT_TIMESTAMP, NULL);

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
    deleted_at   DATETIME     NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_faq_list (category, is_published, sort_order),
    KEY idx_faq_deleted (deleted_at),
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
    sender_relation VARCHAR(12) NULL COMMENT '발신자 관계. stranger/friend/company/operator (관계 기반 알림에만)',
    destination_platform ENUM('ALL', 'MOBILE', 'DESKTOP') NOT NULL DEFAULT 'ALL' COMMENT '알림 노출 플랫폼. ALL은 모든 플랫폼',
    title       VARCHAR(255) NOT NULL,
    message     TEXT         NULL,
    link        VARCHAR(512) NULL,
    is_read     TINYINT(1)   NOT NULL DEFAULT 0,
    read_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notification_user_unread (user_id, is_read, created_at DESC),
    KEY idx_notification_user_platform_unread (user_id, destination_platform, is_read, created_at DESC),
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
    rules_json        JSON        NULL,
    keywords_json     JSON        NULL,
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


-- 12. 개인 차단/허용 정책 (docs/PERSONAL_BLOCK_POLICY.md)
CREATE TABLE IF NOT EXISTS user_block (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '차단을 설정한 사용자',
    blocked_user_id BIGINT       NOT NULL COMMENT '차단 대상 계정',
    flags_json      JSON         NULL COMMENT '표면별 명시 설정. null 항목은 blockedAccount 관계 정책을 따름',
    block_ip        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '이 계정의 접속 IP 도 차단(user_ip_block 파생)',
    memo            VARCHAR(200) NULL COMMENT '개인 메모(차단 사유 등)',
    masked_label    VARCHAR(100) NULL COMMENT '익명 콘텐츠 기반 차단의 표시 라벨(비노출 익명성 유지)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_block_pair (user_id, blocked_user_id),
    KEY idx_user_block_blocked (blocked_user_id),
    CONSTRAINT fk_user_block_user    FOREIGN KEY (user_id)         REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_block_blocked FOREIGN KEY (blocked_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '개인 계정 차단(표면별 세부 설정 포함)';

CREATE TABLE IF NOT EXISTS user_ip_block (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT '차단을 설정한 사용자',
    ip_hash        VARCHAR(64)  NOT NULL COMMENT 'SHA-256(서버솔트+IP). 원본 IP 비저장',
    source_user_id BIGINT       NULL COMMENT '어느 계정 차단에서 파생됐는지',
    label          VARCHAR(100) NULL COMMENT '목록 표기용 라벨',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_ip_block (user_id, ip_hash),
    CONSTRAINT fk_user_ip_block_user   FOREIGN KEY (user_id)        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_ip_block_source FOREIGN KEY (source_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '개인 IP 차단(해시만 저장)';

CREATE TABLE IF NOT EXISTS conversation_block (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    conversation_id BIGINT      NOT NULL,
    flags_json      JSON        NULL COMMENT 'inviteFromRoom/memberCreatedRoomInvite/memberJoinedRoomInvite(+익명 변형)',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_conversation_block (user_id, conversation_id),
    CONSTRAINT fk_conversation_block_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_block_conv FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '개인 채팅방 차단';

CREATE TABLE IF NOT EXISTS user_privacy_policy (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    policy_json JSON     NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_privacy_policy_user (user_id),
    CONSTRAINT fk_user_privacy_policy_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '개인 차단/허용 관계별 정책';

-- 데스크톱 앱 heartbeat — LOCAL 파일 공유는 소유자 데스크톱이 온라인일 때만 전송한다.
CREATE TABLE IF NOT EXISTS user_desktop_presence (
    user_id      BIGINT   NOT NULL,
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_desktop_presence_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '데스크톱 앱 접속 heartbeat(LOCAL 파일 공유 게이트)';

-- 13. 기업 계정 파이프라인 + 채용공고 게시판 (patches/20260705_company_jobboard.sql)
CREATE TABLE IF NOT EXISTS company_application (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL COMMENT '신청자(현재 role=USER) id',
    company_name    VARCHAR(100)  NOT NULL COMMENT '기업명',
    business_number VARCHAR(50)   NULL COMMENT '사업자등록번호(선택)',
    contact         VARCHAR(100)  NOT NULL COMMENT '담당자 연락처(이름/전화/이메일 자유 기재)',
    description     VARCHAR(1000) NULL COMMENT '신청 설명(선택)',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '신청 상태. PENDING/APPROVED/REJECTED',
    reject_reason   VARCHAR(500)  NULL COMMENT '반려 사유(반려 시 필수)',
    reviewed_by     BIGINT        NULL COMMENT '처리한 관리자 id',
    reviewed_at     DATETIME      NULL COMMENT '처리 시각',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_company_application_user (user_id, status),
    KEY idx_company_application_status (status, created_at),
    CONSTRAINT chk_company_application_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_company_application_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_application_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '기업 계정 전환 신청';

CREATE TABLE IF NOT EXISTS user_role_change_history (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL COMMENT '대상 회원 id',
    previous_role VARCHAR(20)  NOT NULL COMMENT '변경 전 role',
    new_role      VARCHAR(20)  NOT NULL COMMENT '변경 후 role',
    reason        VARCHAR(500) NULL COMMENT '변경 사유',
    changed_by    BIGINT       NULL COMMENT '변경 주체(관리자) id. 시스템 변경이면 NULL',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_role_change_user (user_id, created_at),
    CONSTRAINT fk_user_role_change_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_change_actor FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '회원 role 변경 감사 이력';

CREATE TABLE IF NOT EXISTS company_profile (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '기업 계정(users.role=COMPANY) id',
    company_name    VARCHAR(100) NOT NULL COMMENT '기업명',
    business_number VARCHAR(50)  NULL COMMENT '사업자등록번호',
    trust_grade     VARCHAR(20)  NOT NULL DEFAULT 'BASIC' COMMENT '신뢰등급. BASIC/VERIFIED/PARTNER — 공고 승인 정책 입력값',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_profile_user (user_id),
    CONSTRAINT chk_company_profile_grade CHECK (trust_grade IN ('BASIC', 'VERIFIED', 'PARTNER')),
    CONSTRAINT fk_company_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '승인된 기업 프로필(신뢰등급 포함)';

CREATE TABLE IF NOT EXISTS company_job_posting (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    company_user_id  BIGINT       NOT NULL COMMENT '작성 기업 계정 id',
    title            VARCHAR(255) NOT NULL COMMENT '공고 제목',
    job_role         VARCHAR(100) NOT NULL COMMENT '직무명',
    employment_type  VARCHAR(30)  NOT NULL DEFAULT 'FULL_TIME' COMMENT '고용형태. FULL_TIME/CONTRACT/INTERN/PART_TIME/FREELANCE',
    career_level     VARCHAR(30)  NOT NULL DEFAULT 'ANY' COMMENT '경력조건. NEW/EXPERIENCED/ANY',
    career_years_min INT          NULL COMMENT '경력 하한(년). 경력직일 때만 사용',
    career_years_max INT          NULL COMMENT '경력 상한(년)',
    education_level  VARCHAR(30)  NOT NULL DEFAULT 'ANY' COMMENT '학력. ANY/HIGH_SCHOOL/COLLEGE/BACHELOR/MASTER/DOCTOR',
    salary_text      VARCHAR(100) NULL COMMENT '급여 표기(예: 4,000~5,500만원)',
    salary_negotiable TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '급여 협의 가능 플래그',
    work_location    VARCHAR(255) NULL COMMENT '근무지역',
    work_hours       VARCHAR(100) NULL COMMENT '근무시간(예: 주 5일 10:00~19:00)',
    deadline_date    DATE         NULL COMMENT '마감일. 상시 채용이면 NULL 허용',
    always_open      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '상시 채용 플래그',
    main_tasks       TEXT         NULL COMMENT '주요업무',
    requirements     TEXT         NULL COMMENT '자격요건',
    preferred        TEXT         NULL COMMENT '우대사항',
    benefits         TEXT         NULL COMMENT '복리후생',
    hiring_process   TEXT         NULL COMMENT '전형절차',
    headcount        VARCHAR(50)  NULL COMMENT '채용인원(예: 2명, 00명)',
    tags_json        JSON         NULL COMMENT '태그 배열(검색·추천 매칭용)',
    status           VARCHAR(30)  NOT NULL DEFAULT 'DRAFT' COMMENT '상태. DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED/CLOSED',
    reject_reason    VARCHAR(500) NULL COMMENT '반려 사유(신규 등록 반려 시)',
    view_count       INT          NOT NULL DEFAULT 0 COMMENT '상세 조회수',
    published_at     DATETIME     NULL COMMENT '최초 게시 시각',
    closed_at        DATETIME     NULL COMMENT '마감 처리 시각',
    reviewed_by      BIGINT       NULL COMMENT '검토한 관리자 id',
    reviewed_at      DATETIME     NULL COMMENT '검토 시각',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_company_job_posting_company (company_user_id, status),
    KEY idx_company_job_posting_board (status, published_at),
    CONSTRAINT chk_company_job_posting_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'CLOSED')),
    CONSTRAINT fk_company_job_posting_company FOREIGN KEY (company_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_job_posting_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '기업 채용공고 게시판(지원 건 내부 job_posting 과 별개 도메인)';

CREATE TABLE IF NOT EXISTS company_job_posting_revision (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    job_posting_id BIGINT       NOT NULL COMMENT '대상 공고 id',
    payload_json   JSON         NOT NULL COMMENT '변경본 전체 필드 JSON(승인 시 본문에 반영)',
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '검토 상태. PENDING/APPROVED/REJECTED',
    reject_reason  VARCHAR(500) NULL COMMENT '반려 사유',
    reviewed_by    BIGINT       NULL COMMENT '검토한 관리자 id',
    reviewed_at    DATETIME     NULL COMMENT '검토 시각',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_posting_revision_posting (job_posting_id, status),
    CONSTRAINT chk_job_posting_revision_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_job_posting_revision_posting FOREIGN KEY (job_posting_id) REFERENCES company_job_posting (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_posting_revision_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '게시 중 공고의 수정 검토용 변경본';

-- 14. 커뮤니티 스크랩(스냅샷)·구독 (patches/20260705_reactions.sql)
CREATE TABLE IF NOT EXISTS post_scrap (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    post_id               BIGINT       NULL COMMENT '원본 글 링크. 원본이 하드삭제되면 NULL(스냅샷은 유지)',
    snapshot_title        VARCHAR(255) NOT NULL COMMENT '스크랩 시점 제목',
    snapshot_content      MEDIUMTEXT   NOT NULL COMMENT '스크랩 시점 본문',
    snapshot_author_label VARCHAR(100) NOT NULL COMMENT '스크랩 시점 작성자 표시명(익명 글이면 "익명")',
    snapshot_category     VARCHAR(30)  NOT NULL COMMENT '스크랩 시점 카테고리',
    is_anonymous          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '익명 스크랩 — 타인 시점 목록 제외, 집계 포함',
    scrapped_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_post_scrap_user (user_id, scrapped_at DESC),
    KEY idx_post_scrap_post (post_id),
    CONSTRAINT fk_post_scrap_user FOREIGN KEY (user_id) REFERENCES users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_post_scrap_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '게시글 스크랩(스냅샷 보존 — 원본 수정/삭제와 무관)';

CREATE TABLE IF NOT EXISTS post_subscription (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    post_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_subscription (user_id, post_id),
    KEY idx_post_subscription_post (post_id),
    CONSTRAINT fk_post_subscription_user FOREIGN KEY (user_id) REFERENCES users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_post_subscription_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '게시글 구독(새 댓글 알림)';

CREATE TABLE IF NOT EXISTS comment_subscription (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    comment_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_subscription (user_id, comment_id),
    KEY idx_comment_subscription_comment (comment_id),
    CONSTRAINT fk_comment_subscription_user    FOREIGN KEY (user_id)    REFERENCES users (id)             ON DELETE CASCADE,
    CONSTRAINT fk_comment_subscription_comment FOREIGN KEY (comment_id) REFERENCES community_comment (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '댓글 구독(새 답글 알림)';

-- 15. 복수 닉네임 프로필·방 전용 프로필·이력서 상세 (patches/20260705b_profiles.sql)
CREATE TABLE IF NOT EXISTS user_nickname_profile (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT '소유 계정(제재/신고/차단 귀속 단위)',
    nickname       VARCHAR(30)  NOT NULL COMMENT '표시용 닉네임. 전역 UNIQUE',
    avatar_file_id BIGINT       NULL COMMENT '아바타 파일(선택)',
    bio            VARCHAR(200) NULL COMMENT '한 줄 소개',
    is_default     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '기본 프로필 여부(계정당 1개)',
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/HIDDEN',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_nickname_profile_nickname (nickname),
    KEY idx_user_nickname_profile_user (user_id, is_default DESC, created_at),
    CONSTRAINT chk_user_nickname_profile_status CHECK (status IN ('ACTIVE', 'HIDDEN')),
    CONSTRAINT fk_user_nickname_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '복수 닉네임 프로필(표시 계층 — 제재/신고/차단은 계정 단위)';

CREATE TABLE IF NOT EXISTS conversation_member_profile (
    conversation_id     BIGINT   NOT NULL,
    user_id             BIGINT   NOT NULL,
    nickname_profile_id BIGINT   NULL COMMENT '이 방에서 쓸 닉네임 프로필. NULL 이면 익명 참가',
    anonymous           TINYINT(1) NOT NULL DEFAULT 0 COMMENT '익명 참가 여부',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    KEY idx_conversation_member_profile_profile (nickname_profile_id),
    CONSTRAINT fk_conversation_member_profile_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_member_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_member_profile_nickname FOREIGN KEY (nickname_profile_id) REFERENCES user_nickname_profile (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '채팅방 전용 닉네임 프로필 매핑(NULL=익명)';

CREATE TABLE IF NOT EXISTS user_resume_detail (
    user_id            BIGINT   NOT NULL,
    education_json     JSON     NULL COMMENT '[{school, major, gpa, gpaScale, graduationStatus, startDate, endDate}]',
    career_json        JSON     NULL COMMENT '[{company, role, employmentType, startDate, endDate, description}]',
    certificate_json   JSON     NULL COMMENT '[{name, issuer, acquiredAt}]',
    language_json      JSON     NULL COMMENT '[{test, score, acquiredAt}]',
    award_json         JSON     NULL COMMENT '[{title, host, awardedAt, description}]',
    activity_json      JSON     NULL COMMENT '[{title, organization, role, startDate, endDate, description}]',
    skill_json         JSON     NULL COMMENT '["React","Spring",...]',
    portfolio_json     JSON     NULL COMMENT '[{label, url}]',
    desired_condition_json JSON NULL COMMENT '{jobCategoryLarge, jobCategoryMedium, employmentType, region, salaryMin, salaryMax, remote}',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_resume_detail_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '이력서 상세 스펙(사람인/잡코리아식 — 분석 정확도용)';

-- 16. 광고 (patches/20260705b_ads.sql)
CREATE TABLE IF NOT EXISTS advertisement (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    title           VARCHAR(200)  NOT NULL COMMENT '광고 제목(관리자 식별·대체 텍스트)',
    image_file_id   BIGINT        NULL COMMENT '광고 이미지 file_asset id(선택)',
    link_url        VARCHAR(1000) NULL COMMENT '클릭 시 이동 URL',
    placement       VARCHAR(30)   NOT NULL COMMENT '노출 위치. HOME_BANNER/FEED_INLINE/SIDEBAR/INTERSTITIAL',
    target_platform VARCHAR(20)   NOT NULL DEFAULT 'ALL' COMMENT '타겟 플랫폼. WEB/APP/DESKTOP/ALL',
    start_at        DATETIME      NULL COMMENT '게재 시작. NULL=제한 없음',
    end_at          DATETIME      NULL COMMENT '게재 종료. NULL=제한 없음',
    active          TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '활성 여부',
    priority        INT           NOT NULL DEFAULT 0 COMMENT '우선순위(높을수록 먼저)',
    weight          INT           NOT NULL DEFAULT 1 COMMENT '동일 우선순위 가중 랜덤 비중',
    impression_count BIGINT       NOT NULL DEFAULT 0 COMMENT '노출 누적',
    click_count     BIGINT        NOT NULL DEFAULT 0 COMMENT '클릭 누적',
    created_by      BIGINT        NULL COMMENT '등록 관리자 id',
    deleted_at      DATETIME      NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_advertisement_serve (placement, active, start_at, end_at),
    KEY idx_advertisement_platform (target_platform),
    KEY idx_advertisement_deleted (deleted_at),
    CONSTRAINT chk_advertisement_placement CHECK (placement IN ('HOME_BANNER', 'FEED_INLINE', 'SIDEBAR', 'INTERSTITIAL')),
    CONSTRAINT chk_advertisement_platform CHECK (target_platform IN ('WEB', 'APP', 'DESKTOP', 'ALL')),
    CONSTRAINT fk_advertisement_image FOREIGN KEY (image_file_id) REFERENCES file_asset (id) ON DELETE SET NULL,
    CONSTRAINT fk_advertisement_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '광고(배치·플랫폼·기간·집계)';

-- ── 관리자 파리티 이식 정책 테이블(트립투게더) — patches 20260706_runtime_setting / 20260706c / 20260706d ──

-- key-value 런타임 설정(코드가 DB→fallback→코드기본값 순 참조) + 변경 이력
CREATE TABLE IF NOT EXISTS application_runtime_setting (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    setting_key    VARCHAR(160) NOT NULL COMMENT '설정 키(예: oauth.kakao.client-id)',
    setting_group  VARCHAR(60)  NOT NULL DEFAULT 'GENERAL',
    display_name   VARCHAR(160) NOT NULL,
    setting_value  TEXT         NULL COMMENT 'DB 우선 설정값',
    fallback_value TEXT         NULL COMMENT '설정값이 비면 쓸 fallback',
    value_type     VARCHAR(30)  NOT NULL DEFAULT 'STRING' COMMENT 'STRING/NUMBER/BOOLEAN/URL/SECRET',
    secret         TINYINT(1)   NOT NULL DEFAULT 0,
    editable       TINYINT(1)   NOT NULL DEFAULT 1,
    active         TINYINT(1)   NOT NULL DEFAULT 1,
    description    VARCHAR(1000) NULL,
    updated_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_runtime_setting_key (setting_key),
    KEY idx_application_runtime_setting_group (setting_group, active),
    CONSTRAINT fk_application_runtime_setting_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS application_runtime_setting_history (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    setting_id          BIGINT       NULL,
    setting_key         VARCHAR(160) NOT NULL,
    version_no          INT          NOT NULL DEFAULT 1,
    change_type         VARCHAR(30)  NOT NULL COMMENT 'CREATE/UPDATE/IMPORT/RESET',
    reason              VARCHAR(500) NULL COMMENT '변경 사유(감사)',
    actor_user_id       BIGINT       NULL,
    before_value        TEXT         NULL,
    after_value         TEXT         NULL,
    before_fallback     TEXT         NULL,
    after_fallback      TEXT         NULL,
    before_snapshot     JSON         NULL,
    after_snapshot      JSON         NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_application_runtime_setting_history_key (setting_key, created_at),
    CONSTRAINT fk_application_runtime_setting_history_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 로그인 위험도(브루트포스) 잠금 정책(단일 행). OFF=무제약, ON=연속 실패 N회 시 M분 잠금.
CREATE TABLE IF NOT EXISTS login_risk_policy (
    id               TINYINT   NOT NULL,
    enabled          TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=무제약, 1=자동 잠금 집행',
    max_failed_count INT       NOT NULL DEFAULT 5  COMMENT '자동 잠금 트리거 연속 실패 횟수',
    lock_minutes     INT       NOT NULL DEFAULT 10 COMMENT '자동 잠금 유지 분',
    updated_by       BIGINT    NULL,
    created_at       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_login_risk_policy_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
INSERT INTO login_risk_policy (id, enabled, max_failed_count, lock_minutes) VALUES (1, 1, 5, 10)
ON DUPLICATE KEY UPDATE id = id;

-- AI 챗봇 일일 사용 쿼터 정책(단일 행). 기본 OFF(무제약=현재 동작).
CREATE TABLE IF NOT EXISTS chatbot_quota_policy (
    id          TINYINT   NOT NULL,
    enabled     TINYINT(1) NOT NULL DEFAULT 0  COMMENT '0=무제약, 1=일일 쿼터 집행',
    daily_limit INT       NOT NULL DEFAULT 100 COMMENT '로그인 사용자 1인 하루 허용 질문 수',
    updated_by  BIGINT    NULL,
    created_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_quota_policy_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
INSERT INTO chatbot_quota_policy (id, enabled, daily_limit) VALUES (1, 0, 100)
ON DUPLICATE KEY UPDATE id = id;


-- ── team1_db 실적용 반영: 패치로만 있던 테이블 38종을 baseline 에 편입(fresh install ↔ team1_db 일치) ──
-- (team1_db SHOW CREATE TABLE 기준 authoritative DDL. 소유 도메인별 패치와 동일)

CREATE TABLE IF NOT EXISTS `ad_impression_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `campaign_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `surface` varchar(20) NOT NULL,
  `event_type` varchar(20) NOT NULL DEFAULT 'IMPRESSION',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ad_impression_campaign` (`campaign_id`,`created_at` DESC),
  KEY `idx_ad_impression_user` (`user_id`,`created_at` DESC),
  CONSTRAINT `fk_ad_impression_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `admin_ad_campaign` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ad_impression_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `admin_action_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '관리자 액션 로그 ID',
  `actor_user_id` bigint DEFAULT NULL COMMENT '액션을 수행한 관리자 ID',
  `target_user_id` bigint DEFAULT NULL COMMENT '대상 회원 또는 관리자 ID',
  `action_type` varchar(80) NOT NULL COMMENT '액션 유형',
  `target_type` varchar(80) DEFAULT NULL COMMENT '대상 유형',
  `before_value` json DEFAULT NULL COMMENT '변경 전 값',
  `after_value` json DEFAULT NULL COMMENT '변경 후 값',
  `reason` varchar(500) DEFAULT NULL COMMENT '처리 사유',
  `ip_address` varchar(64) DEFAULT NULL COMMENT '요청 IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '요청 User-Agent',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '기록 시각',
  PRIMARY KEY (`id`),
  KEY `idx_admin_action_actor` (`actor_user_id`,`created_at`),
  KEY `idx_admin_action_target` (`target_user_id`,`created_at`),
  KEY `idx_admin_action_type` (`action_type`,`created_at`),
  CONSTRAINT `fk_admin_action_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_action_target` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 기능 실행 감사 로그';
CREATE TABLE IF NOT EXISTS `admin_ad_campaign` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(160) NOT NULL,
  `body` varchar(500) DEFAULT NULL,
  `surface` varchar(20) NOT NULL DEFAULT 'WEB',
  `placement` varchar(80) NOT NULL DEFAULT 'GLOBAL_TOP',
  `creative_type` varchar(20) NOT NULL DEFAULT 'BANNER',
  `image_url` varchar(512) DEFAULT NULL,
  `target_url` varchar(512) DEFAULT NULL,
  `visible_to_plans_json` json DEFAULT NULL,
  `starts_at` datetime DEFAULT NULL,
  `ends_at` datetime DEFAULT NULL,
  `priority` int NOT NULL DEFAULT '100',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_ad_surface_active` (`surface`,`active`,`priority`),
  KEY `idx_admin_ad_schedule` (`starts_at`,`ends_at`),
  KEY `fk_admin_ad_created_by` (`created_by`),
  KEY `fk_admin_ad_updated_by` (`updated_by`),
  CONSTRAINT `fk_admin_ad_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_ad_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `admin_block_access_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(64) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `request_uri` varchar(512) DEFAULT NULL,
  `http_method` varchar(10) DEFAULT NULL,
  `block_kind` varchar(20) NOT NULL COMMENT 'IP/USER',
  `block_match_type` varchar(20) DEFAULT NULL COMMENT 'SINGLE_IP/CIDR/RANGE/COUNTRY/ASN/ACCOUNT_STATUS',
  `block_target_key` varchar(160) DEFAULT NULL,
  `block_rule_id` bigint DEFAULT NULL,
  `block_reason` varchar(1000) DEFAULT NULL,
  `client_ip` varchar(64) DEFAULT NULL,
  `country_code` varchar(8) DEFAULT NULL,
  `asn` varchar(20) DEFAULT NULL,
  `cache_source` varchar(20) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_block_access_log_time` (`created_at`),
  KEY `idx_admin_block_access_log_ip` (`client_ip`,`created_at`),
  KEY `idx_admin_block_access_log_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `admin_ip_block_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_code` varchar(80) NOT NULL COMMENT '?? ?? ??(?: MANUAL_FEED_20260706_1)',
  `batch_name` varchar(160) NOT NULL,
  `source_type` varchar(40) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/POLICY_FEED_CSV/POLICY_FEED_JSON/POLICY_FEED_API',
  `source_name` varchar(200) DEFAULT NULL COMMENT '?? ??(??????? ?)',
  `rule_action` varchar(30) NOT NULL DEFAULT 'BLOCK' COMMENT '?? ?? ?? ?? BLOCK/ALLOWLIST/REVIEW',
  `default_priority` int NOT NULL DEFAULT '100',
  `active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '?? ON/OFF. OFF ? ?? ?? ????? ??',
  `total_rule_count` int NOT NULL DEFAULT '0',
  `active_rule_count` int NOT NULL DEFAULT '0',
  `memo` varchar(2000) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_ip_block_batch_code` (`batch_code`),
  KEY `idx_admin_ip_block_batch_active` (`active`,`created_at`),
  KEY `fk_admin_ip_block_batch_created_by` (`created_by`),
  KEY `fk_admin_ip_block_batch_updated_by` (`updated_by`),
  CONSTRAINT `fk_admin_ip_block_batch_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_ip_block_batch_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `admin_ip_block_batch_operation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `operation_type` varchar(40) NOT NULL COMMENT 'CREATE/IMPORT/TOGGLE_ON/TOGGLE_OFF',
  `operation_option` varchar(40) DEFAULT NULL COMMENT 'cascade ??: BATCH_ONLY/CASCADE_ACTIVE_RULES/RESTORE_BATCH_CONTROL/FORCE_ENABLE_ALL',
  `requested_rule_count` int NOT NULL DEFAULT '0',
  `affected_rule_count` int NOT NULL DEFAULT '0',
  `actor_user_id` bigint DEFAULT NULL,
  `memo` varchar(2000) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_ip_block_batch_op_batch` (`batch_id`,`created_at`),
  KEY `fk_admin_ip_block_batch_op_actor` (`actor_user_id`),
  CONSTRAINT `fk_admin_ip_block_batch_op_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_ip_block_batch_op_batch` FOREIGN KEY (`batch_id`) REFERENCES `admin_ip_block_batch` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `admin_ip_block_batch_operation_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operation_id` bigint NOT NULL,
  `rule_id` bigint DEFAULT NULL,
  `operation_effect` varchar(40) NOT NULL COMMENT 'CREATED/ENABLED/DISABLED/SKIPPED_OVERRIDE',
  `before_is_active` tinyint(1) DEFAULT NULL,
  `after_is_active` tinyint(1) DEFAULT NULL,
  `before_control_mode` varchar(30) DEFAULT NULL,
  `after_control_mode` varchar(30) DEFAULT NULL,
  `memo` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_ip_block_batch_op_rule_op` (`operation_id`),
  CONSTRAINT `fk_admin_ip_block_batch_op_rule_op` FOREIGN KEY (`operation_id`) REFERENCES `admin_ip_block_batch_operation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `admin_permission_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '권한 변경 이력 ID',
  `actor_user_id` bigint DEFAULT NULL COMMENT '처리 관리자 ID',
  `target_user_id` bigint DEFAULT NULL COMMENT '대상 관리자 ID',
  `action_type` varchar(80) NOT NULL COMMENT '권한 액션 유형',
  `permission_code` varchar(80) DEFAULT NULL COMMENT '권한 코드',
  `group_code` varchar(80) DEFAULT NULL COMMENT '권한 그룹 코드',
  `reason` varchar(500) DEFAULT NULL COMMENT '처리 사유',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '기록 시각',
  PRIMARY KEY (`id`),
  KEY `idx_admin_perm_audit_target` (`target_user_id`,`created_at`),
  KEY `idx_admin_perm_audit_actor` (`actor_user_id`,`created_at`),
  CONSTRAINT `fk_admin_perm_audit_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_perm_audit_target` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 권한 변경 감사 이력';
CREATE TABLE IF NOT EXISTS `admin_permission_group` (
  `group_code` varchar(80) NOT NULL COMMENT '권한 그룹 코드',
  `display_name` varchar(100) NOT NULL COMMENT '권한 그룹명',
  `description` varchar(500) DEFAULT NULL COMMENT '권한 그룹 설명',
  `role_scope` varchar(20) NOT NULL DEFAULT 'ADMIN' COMMENT '권한 그룹을 부여할 수 있는 최소 역할. ADMIN/SUPER_ADMIN',
  `display_order` int NOT NULL DEFAULT '0' COMMENT '권한 그룹 표시 순서',
  `active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '활성 여부',
  `created_by` bigint DEFAULT NULL COMMENT '생성 관리자 ID',
  `updated_by` bigint DEFAULT NULL COMMENT '수정 관리자 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
  PRIMARY KEY (`group_code`),
  KEY `idx_admin_perm_group_active` (`active`),
  KEY `fk_admin_perm_group_created_by` (`created_by`),
  KEY `fk_admin_perm_group_updated_by` (`updated_by`),
  CONSTRAINT `fk_admin_perm_group_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_perm_group_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 권한 그룹';
CREATE TABLE IF NOT EXISTS `admin_permission_group_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '권한 그룹 항목 ID',
  `group_code` varchar(80) NOT NULL COMMENT '권한 그룹 코드',
  `permission_code` varchar(80) NOT NULL COMMENT '권한 코드',
  `created_by` bigint DEFAULT NULL COMMENT '추가 관리자 ID',
  `deleted_at` datetime DEFAULT NULL COMMENT '권한 그룹 항목 소프트 삭제 시각',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '추가일',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_perm_group_item` (`group_code`,`permission_code`),
  KEY `fk_admin_perm_group_item_perm` (`permission_code`),
  KEY `fk_admin_perm_group_item_created_by` (`created_by`),
  KEY `idx_admin_perm_group_item_deleted` (`deleted_at`),
  CONSTRAINT `fk_admin_perm_group_item_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_perm_group_item_group` FOREIGN KEY (`group_code`) REFERENCES `admin_permission_group` (`group_code`) ON DELETE CASCADE,
  CONSTRAINT `fk_admin_perm_group_item_perm` FOREIGN KEY (`permission_code`) REFERENCES `admin_permission_policy` (`permission_code`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='권한 그룹에 포함된 개별 권한';
CREATE TABLE IF NOT EXISTS `admin_permission_menu_group` (
  `menu_group_code` varchar(80) NOT NULL COMMENT '관리자 메뉴 그룹 코드. MEMBER/AI/BILLING/CONTENT/AUDIT/POLICY 등',
  `display_name` varchar(100) NOT NULL COMMENT '관리자 화면에 표시할 메뉴 그룹명',
  `description` varchar(500) DEFAULT NULL COMMENT '해당 메뉴 그룹이 담당하는 운영 범위 설명',
  `display_order` int NOT NULL DEFAULT '0' COMMENT '관리자 사이드바와 권한 관리 화면 표시 순서',
  `active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '메뉴 그룹 사용 여부',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
  PRIMARY KEY (`menu_group_code`),
  KEY `idx_admin_perm_menu_group_active` (`active`,`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 권한 코드가 속하는 메뉴 그룹 메타데이터';
CREATE TABLE IF NOT EXISTS `admin_permission_policy` (
  `permission_code` varchar(80) NOT NULL COMMENT '권한 코드',
  `display_name` varchar(100) NOT NULL COMMENT '권한명',
  `description` varchar(500) DEFAULT NULL COMMENT '권한 설명',
  `menu_group_code` varchar(80) DEFAULT NULL COMMENT '권한이 속한 관리자 메뉴 그룹 코드. admin_permission_menu_group.menu_group_code 참조용',
  `display_order` int NOT NULL DEFAULT '0' COMMENT '권한 관리 화면 표시 순서',
  `active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '활성 여부',
  `created_by` bigint DEFAULT NULL COMMENT '생성 관리자 ID',
  `updated_by` bigint DEFAULT NULL COMMENT '수정 관리자 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
  PRIMARY KEY (`permission_code`),
  KEY `idx_admin_perm_active` (`active`),
  KEY `fk_admin_perm_created_by` (`created_by`),
  KEY `fk_admin_perm_updated_by` (`updated_by`),
  CONSTRAINT `fk_admin_perm_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_perm_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 개별 권한 정책';
CREATE TABLE IF NOT EXISTS `admin_permission_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '권한을 받을 대상 관리자',
  `permission_code` varchar(80) NOT NULL COMMENT '요청 권한 코드',
  `description` varchar(500) DEFAULT NULL COMMENT '요청 사유',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `requested_by` bigint DEFAULT NULL COMMENT '요청자',
  `decided_by` bigint DEFAULT NULL COMMENT '승인/거절 처리자',
  `decided_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_permission_request_status` (`status`,`created_at`),
  KEY `idx_admin_permission_request_user` (`user_id`,`created_at`),
  KEY `fk_admin_permission_request_code` (`permission_code`),
  KEY `fk_admin_permission_request_requested_by` (`requested_by`),
  KEY `fk_admin_permission_request_decided_by` (`decided_by`),
  CONSTRAINT `fk_admin_permission_request_code` FOREIGN KEY (`permission_code`) REFERENCES `admin_permission_policy` (`permission_code`) ON DELETE CASCADE,
  CONSTRAINT `fk_admin_permission_request_decided_by` FOREIGN KEY (`decided_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_permission_request_requested_by` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_permission_request_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `admin_security_appeal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `public_request_id` varchar(60) NOT NULL,
  `subject_type` varchar(40) NOT NULL,
  `subject_value` varchar(180) NOT NULL,
  `block_rule_id` bigint DEFAULT NULL,
  `submitter_email` varchar(255) NOT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'OPEN',
  `reason` varchar(2000) DEFAULT NULL,
  `decision_reason` varchar(2000) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_security_appeal_public_id` (`public_request_id`),
  KEY `idx_admin_security_appeal_status` (`status`,`created_at`),
  KEY `idx_admin_security_appeal_subject` (`subject_type`,`subject_value`),
  KEY `fk_admin_security_appeal_rule` (`block_rule_id`),
  KEY `fk_admin_security_appeal_reviewed_by` (`reviewed_by`),
  CONSTRAINT `fk_admin_security_appeal_reviewed_by` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_security_appeal_rule` FOREIGN KEY (`block_rule_id`) REFERENCES `admin_security_block_rule` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='?? ?? ???? ?? ?';
CREATE TABLE IF NOT EXISTS `admin_security_appeal_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `policy_code` varchar(60) NOT NULL DEFAULT 'SECURITY_APPEAL_DEFAULT',
  `display_name` varchar(120) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `allow_multiple_open` tinyint(1) NOT NULL DEFAULT '0',
  `max_open_per_subject` int NOT NULL DEFAULT '1',
  `submitter_daily_limit` int NOT NULL DEFAULT '3',
  `token_ttl_hours` int NOT NULL DEFAULT '24',
  `config_json` json NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_security_appeal_policy_code` (`policy_code`),
  KEY `fk_admin_security_appeal_policy_updated_by` (`updated_by`),
  CONSTRAINT `fk_admin_security_appeal_policy_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='?? ?? ???? ??';
CREATE TABLE IF NOT EXISTS `admin_security_block_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_type` varchar(30) NOT NULL COMMENT 'USER/EMAIL/EMAIL_DOMAIN/IP/CIDR/IP_RANGE/COUNTRY/ASN',
  `rule_value` varchar(160) NOT NULL COMMENT '?? ?? ?. IP, CIDR, ????, ??? ??? ?',
  `scope` varchar(30) NOT NULL DEFAULT 'GLOBAL' COMMENT 'GLOBAL/LOGIN/COMMUNITY/AI/SUPPORT',
  `action_type` varchar(30) NOT NULL DEFAULT 'BLOCK' COMMENT 'BLOCK/REVIEW/CHALLENGE/ALLOWLIST',
  `category` varchar(40) NOT NULL DEFAULT 'MANUAL' COMMENT 'SPAM/ABUSE/BRUTE_FORCE/GEO/VPN/SECURITY/MANUAL',
  `priority` int NOT NULL DEFAULT '100' COMMENT '?? ????(DESC). ???? ?? ??',
  `control_mode` varchar(30) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/BATCH/MANUAL_OVERRIDE',
  `batch_id` bigint DEFAULT NULL COMMENT '? ??? ?? IP ?? ??(admin_ip_block_batch.id)',
  `reason` varchar(1000) DEFAULT NULL,
  `memo` varchar(2000) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `is_effective_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '?? ???????????? ??? ?? ????(?? ?? ??)',
  `waf_sync_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `waf_sync_status` varchar(30) NOT NULL DEFAULT 'SKIPPED',
  `waf_rule_id` varchar(120) DEFAULT NULL,
  `last_synced_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_security_block_rule_active` (`active`,`rule_type`,`rule_value`),
  KEY `idx_admin_security_block_rule_waf` (`waf_sync_enabled`,`waf_sync_status`),
  KEY `idx_admin_security_block_rule_expires` (`expires_at`),
  KEY `fk_admin_security_block_rule_created_by` (`created_by`),
  KEY `fk_admin_security_block_rule_updated_by` (`updated_by`),
  KEY `idx_admin_security_block_rule_effective` (`is_effective_active`,`priority`),
  KEY `idx_admin_security_block_rule_batch` (`batch_id`),
  CONSTRAINT `fk_admin_security_block_rule_batch` FOREIGN KEY (`batch_id`) REFERENCES `admin_ip_block_batch` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_security_block_rule_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_security_block_rule_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='??? ?? ??/?? ??';
CREATE TABLE IF NOT EXISTS `admin_security_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(60) NOT NULL,
  `display_name` varchar(120) NOT NULL,
  `provider_type` varchar(30) NOT NULL COMMENT 'WAF/RISK/EMAIL/CAPTCHA',
  `mode` varchar(30) NOT NULL DEFAULT 'MOCK',
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `endpoint_url` varchar(500) DEFAULT NULL,
  `config_json` json NOT NULL,
  `health_status` varchar(30) NOT NULL DEFAULT 'UNKNOWN',
  `last_checked_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_security_provider_code` (`provider_code`),
  KEY `idx_admin_security_provider_type` (`provider_type`,`enabled`),
  KEY `fk_admin_security_provider_updated_by` (`updated_by`),
  CONSTRAINT `fk_admin_security_provider_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='??/WAF/???? ?? Provider ??';
CREATE TABLE IF NOT EXISTS `admin_security_provider_health_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_config_id` bigint NOT NULL,
  `provider_code` varchar(60) NOT NULL,
  `provider_type` varchar(30) NOT NULL,
  `check_source` varchar(30) NOT NULL DEFAULT 'MANUAL',
  `status_before` varchar(30) DEFAULT NULL,
  `status_after` varchar(30) NOT NULL,
  `detail_message` varchar(1000) DEFAULT NULL,
  `actor_user_id` bigint DEFAULT NULL,
  `checked_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_security_provider_health_provider` (`provider_code`,`checked_at`),
  KEY `idx_admin_security_provider_health_status` (`status_after`,`checked_at`),
  KEY `idx_admin_security_provider_health_actor` (`actor_user_id`,`checked_at`),
  KEY `fk_admin_security_provider_health_provider` (`provider_config_id`),
  CONSTRAINT `fk_admin_security_provider_health_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_security_provider_health_provider` FOREIGN KEY (`provider_config_id`) REFERENCES `admin_security_provider_config` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Provider ??/???? ???? ?? ??';
CREATE TABLE IF NOT EXISTS `admin_security_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_type` varchar(40) NOT NULL COMMENT 'LOGIN_RISK/EXTERNAL_RISK/SECURITY_RISK/GENERAL',
  `subject_type` varchar(40) NOT NULL COMMENT 'USER/IP/EMAIL/POST/COMMENT/SESSION',
  `subject_value` varchar(180) NOT NULL,
  `risk_score` int NOT NULL DEFAULT '0',
  `risk_level` varchar(20) NOT NULL DEFAULT 'LOW',
  `status` varchar(30) NOT NULL DEFAULT 'OPEN',
  `decision_action` varchar(30) DEFAULT NULL COMMENT 'ALLOW/BLOCK/CHALLENGE/ESCALATE/DISMISS',
  `reason` varchar(1000) DEFAULT NULL,
  `evidence_json` json NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `assigned_to` bigint DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_security_review_status` (`status`,`risk_level`,`created_at`),
  KEY `idx_admin_security_review_subject` (`subject_type`,`subject_value`),
  KEY `fk_admin_security_review_created_by` (`created_by`),
  KEY `fk_admin_security_review_assigned_to` (`assigned_to`),
  KEY `fk_admin_security_review_decided_by` (`decided_by`),
  CONSTRAINT `fk_admin_security_review_assigned_to` FOREIGN KEY (`assigned_to`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_security_review_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_security_review_decided_by` FOREIGN KEY (`decided_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='???/??/?? ?? ?? ?? ?';
CREATE TABLE IF NOT EXISTS `admin_security_waf_sync_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `block_rule_id` bigint DEFAULT NULL,
  `provider_code` varchar(60) NOT NULL DEFAULT 'MANUAL_WAF',
  `operation_type` varchar(30) NOT NULL COMMENT 'UPSERT/DELETE/HEALTH_CHECK',
  `status` varchar(30) NOT NULL DEFAULT 'QUEUED',
  `request_payload_json` json DEFAULT NULL,
  `response_payload_json` json DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `requested_by` bigint DEFAULT NULL,
  `requested_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `processed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_security_waf_event_rule` (`block_rule_id`,`requested_at`),
  KEY `idx_admin_security_waf_event_status` (`status`,`requested_at`),
  KEY `fk_admin_security_waf_event_requested_by` (`requested_by`),
  CONSTRAINT `fk_admin_security_waf_event_requested_by` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_security_waf_event_rule` FOREIGN KEY (`block_rule_id`) REFERENCES `admin_security_block_rule` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='WAF ??? ?? ? ?? ??';
CREATE TABLE IF NOT EXISTS `admin_system_policy` (
  `policy_code` varchar(80) NOT NULL COMMENT '운영 정책 코드',
  `display_name` varchar(100) NOT NULL COMMENT '정책명',
  `description` varchar(500) DEFAULT NULL COMMENT '정책 설명',
  `config_json` json DEFAULT NULL COMMENT '정책 설정 JSON',
  `schedule_type` varchar(30) NOT NULL DEFAULT 'MANUAL' COMMENT '실행 방식. MANUAL/DAILY/WEEKLY/MONTHLY',
  `schedule_interval_hours` int DEFAULT NULL COMMENT '반복 실행 간격(시간)',
  `schedule_day_of_month` int DEFAULT NULL COMMENT '월간 실행일',
  `schedule_time` varchar(10) DEFAULT NULL COMMENT '실행 시각 HH:mm',
  `active` tinyint(1) NOT NULL DEFAULT '0' COMMENT '정책 활성 여부',
  `last_run_at` datetime DEFAULT NULL COMMENT '마지막 실행 시각',
  `last_run_status` varchar(20) DEFAULT NULL COMMENT '마지막 실행 상태',
  `last_run_message` varchar(1000) DEFAULT NULL COMMENT '마지막 실행 메시지',
  `updated_by` bigint DEFAULT NULL COMMENT '마지막 수정 관리자 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
  PRIMARY KEY (`policy_code`),
  KEY `idx_admin_system_policy_active` (`active`),
  KEY `fk_admin_system_policy_updated_by` (`updated_by`),
  CONSTRAINT `fk_admin_system_policy_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 운영 정책';
CREATE TABLE IF NOT EXISTS `admin_user_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '관리자 그룹 소속 ID',
  `user_id` bigint NOT NULL COMMENT '관리자 회원 ID',
  `group_code` varchar(80) NOT NULL COMMENT '권한 그룹 코드',
  `granted_by` bigint DEFAULT NULL COMMENT '배정 관리자 ID',
  `granted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '배정일',
  `revoked_at` datetime DEFAULT NULL COMMENT '해제일',
  `active_assignment_key` tinyint GENERATED ALWAYS AS ((CASE WHEN `revoked_at` IS NULL THEN 1 ELSE NULL END)) STORED COMMENT '활성 배정 1건 유니크 보장용',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_user_group_active` (`user_id`,`group_code`,`active_assignment_key`),
  KEY `idx_admin_user_group_user` (`user_id`,`revoked_at`),
  KEY `fk_admin_user_group_group` (`group_code`),
  KEY `fk_admin_user_group_granted_by` (`granted_by`),
  CONSTRAINT `fk_admin_user_group_granted_by` FOREIGN KEY (`granted_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_user_group_group` FOREIGN KEY (`group_code`) REFERENCES `admin_permission_group` (`group_code`) ON DELETE CASCADE,
  CONSTRAINT `fk_admin_user_group_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 권한 그룹 소속';
CREATE TABLE IF NOT EXISTS `admin_user_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '관리자 직접 권한 ID',
  `user_id` bigint NOT NULL COMMENT '관리자 회원 ID',
  `permission_code` varchar(80) NOT NULL COMMENT '권한 코드',
  `granted_by` bigint DEFAULT NULL COMMENT '부여 관리자 ID',
  `granted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '부여일',
  `revoked_at` datetime DEFAULT NULL COMMENT '회수일',
  `active_assignment_key` tinyint GENERATED ALWAYS AS ((CASE WHEN `revoked_at` IS NULL THEN 1 ELSE NULL END)) STORED COMMENT '활성 배정 1건 유니크 보장용',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_user_perm_active` (`user_id`,`permission_code`,`active_assignment_key`),
  KEY `idx_admin_user_perm_user` (`user_id`,`revoked_at`),
  KEY `fk_admin_user_perm_code` (`permission_code`),
  KEY `fk_admin_user_perm_granted_by` (`granted_by`),
  CONSTRAINT `fk_admin_user_perm_code` FOREIGN KEY (`permission_code`) REFERENCES `admin_permission_policy` (`permission_code`) ON DELETE CASCADE,
  CONSTRAINT `fk_admin_user_perm_granted_by` FOREIGN KEY (`granted_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_user_perm_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자에게 직접 부여된 권한';
CREATE TABLE IF NOT EXISTS `chatbot_conversation_memory` (
  `conversation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '대화 세션 ID (서버 발급)',
  `user_id` bigint DEFAULT NULL COMMENT '대화 소유자(로그인 유저 id). NULL=익명 세션(복원 대상 아님)',
  `application_case_id` bigint DEFAULT NULL COMMENT '연결된 지원 건 id(application_case 논리 참조, FK 없음). NULL=잡담/FAQ 대화',
  `title` varchar(255) DEFAULT NULL COMMENT '세션 제목(목록 표시용). NULL=미생성, 자동 생성은 다음 Phase',
  `onboarding_declined_at` datetime DEFAULT NULL COMMENT '깡통계정 온보딩을 "그만"으로 거부한 시각. NULL=거부 안 함 → 이후 이 대화는 온보딩 재권유 안 함',
  `messages_json` json NOT NULL COMMENT 'LangChain4j 메시지 윈도우 JSON',
  `deleted_at` datetime DEFAULT NULL COMMENT '대화 소프트 삭제 시각',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`conversation_id`),
  KEY `idx_ccm_user_updated` (`user_id`,`updated_at`),
  KEY `idx_ccm_case` (`application_case_id`),
  KEY `idx_ccm_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `chatbot_intake_slot` (
  `conversation_id` bigint NOT NULL COMMENT '세션(대화) id — chatbot_conversation_memory 논리 참조(FK 없음), 1:1',
  `user_id` bigint DEFAULT NULL COMMENT '슬롯 소유자(로그인 유저 id). 비로그인 NULL',
  `application_case_id` bigint DEFAULT NULL COMMENT '확정 지원 건 id(application_case 논리 참조, FK 없음). 미확정 NULL',
  `mode` varchar(20) DEFAULT NULL COMMENT '면접 모드: BASIC/JOB/PERSONALITY/PRESSURE/RESUME/COMPANY',
  `status` varchar(12) NOT NULL DEFAULT 'PENDING' COMMENT '인테이크 세션 단계: PENDING(진행·복원대상)/READY(슬롯충족·라우터정상)/DONE(예약)',
  `original_query` varchar(1000) DEFAULT NULL COMMENT '사용자 첫 발화(verbatim)',
  `fetched_cases` json DEFAULT NULL COMMENT '후보 지원건 목록 캐시(JSON). 재조회로 대체 가능한 파생 캐시',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `chatbot_response_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint DEFAULT NULL COMMENT '발생 대화 id(있으면) — chatbot_conversation_memory 논리 참조(FK 없음)',
  `user_id` bigint DEFAULT NULL COMMENT '로그인 유저 id, 비로그인 NULL',
  `question` varchar(1000) NOT NULL COMMENT '사용자 질문(verbatim)',
  `response_path` varchar(16) NOT NULL COMMENT '응답 경로: NAV_FAST/FAQ_FAST/AGENT',
  `faq_referenced` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'FAQ 근거로 답함(=자동 해결)',
  `top_similarity` double DEFAULT NULL COMMENT 'FAQ 최고 유사도(슬라이더 미리보기 소스; 계산 불가 시 NULL)',
  `matched_faq_id` bigint DEFAULT NULL COMMENT '인용한 최상위 FAQ id(참조 대화 표시; 없으면 NULL)',
  `handoff` tinyint(1) NOT NULL DEFAULT '0' COMMENT '상담사/문의 전환 여부(전환율 분자)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_crl_created` (`created_at`),
  KEY `idx_crl_ref_created` (`faq_referenced`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `chatbot_unanswered_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question` varchar(1000) NOT NULL COMMENT '질문 원문(verbatim)',
  `question_norm` varchar(255) NOT NULL COMMENT '정규화 키(trim+소문자+공백제거, 255 초과 절단) — 빈도 집계용',
  `top_similarity` double DEFAULT NULL COMMENT 'FAQ 최고 유사도(임계 미달; 계산 불가 시 NULL)',
  `embedding` mediumtext COMMENT '질문 임베딩(bge-m3) JSON 배열 — 군집화용. 임베딩 불가 시 NULL',
  `best_faq_id` bigint DEFAULT NULL COMMENT '미스 당시 가장 가까웠던 FAQ id(있으면) — best 표시·카테고리 근거',
  `user_id` bigint DEFAULT NULL COMMENT '로그인 유저 id, 비로그인 NULL',
  `conversation_id` bigint DEFAULT NULL COMMENT '발생 대화 id(있으면)',
  `source` varchar(20) NOT NULL DEFAULT 'FAQ_FASTPATH' COMMENT '미스 발생 경로',
  `status` varchar(20) NOT NULL DEFAULT 'NEW' COMMENT 'NEW/REVIEWED/CONVERTED/DISMISSED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cuq_norm` (`question_norm`),
  KEY `idx_cuq_status_created` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `comment_ai_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL,
  `task_type` varchar(30) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `result_json` json DEFAULT NULL,
  `model` varchar(80) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_ai_result_task` (`comment_id`,`task_type`),
  KEY `idx_comment_ai_result_status` (`task_type`,`status`,`completed_at`),
  CONSTRAINT `fk_comment_ai_result_comment` FOREIGN KEY (`comment_id`) REFERENCES `community_comment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `community_post_embedding` (
  `post_id` bigint NOT NULL COMMENT 'community_post.id',
  `embedding` json NOT NULL COMMENT 'bge-m3 임베딩 벡터 (1024차원 float 배열)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`post_id`),
  CONSTRAINT `fk_cpe_post` FOREIGN KEY (`post_id`) REFERENCES `community_post` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `enterprise_account_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `company_name` varchar(160) NOT NULL,
  `business_number` varchar(80) DEFAULT NULL,
  `representative_name` varchar(100) DEFAULT NULL,
  `contact_name` varchar(100) DEFAULT NULL,
  `contact_email` varchar(255) DEFAULT NULL,
  `contact_phone` varchar(40) DEFAULT NULL,
  `website_url` varchar(512) DEFAULT NULL,
  `industry` varchar(120) DEFAULT NULL,
  `employee_count` varchar(60) DEFAULT NULL,
  `evidence_file_url` varchar(512) DEFAULT NULL,
  `requested_policy_json` json DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `review_memo` varchar(1000) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_enterprise_application_user` (`user_id`,`created_at` DESC),
  KEY `idx_enterprise_application_status` (`status`,`created_at` DESC),
  KEY `fk_enterprise_application_reviewer` (`reviewed_by`),
  CONSTRAINT `fk_enterprise_application_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_enterprise_application_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `enterprise_job_policy` (
  `user_id` bigint NOT NULL,
  `trusted` tinyint(1) NOT NULL DEFAULT '0',
  `create_requires_review` tinyint(1) NOT NULL DEFAULT '1',
  `edit_requires_review` tinyint(1) NOT NULL DEFAULT '1',
  `max_active_posts` int NOT NULL DEFAULT '5',
  `updated_by` bigint DEFAULT NULL,
  `update_reason` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  KEY `fk_enterprise_job_policy_admin` (`updated_by`),
  CONSTRAINT `fk_enterprise_job_policy_admin` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_enterprise_job_policy_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `enterprise_job_posting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_user_id` bigint NOT NULL,
  `company_name` varchar(160) NOT NULL,
  `title` varchar(180) NOT NULL,
  `position_title` varchar(160) NOT NULL,
  `job_category` varchar(120) DEFAULT NULL,
  `specialties_json` json DEFAULT NULL,
  `duties` mediumtext NOT NULL,
  `qualifications` mediumtext,
  `preferred` mediumtext,
  `benefits` mediumtext,
  `employment_type` varchar(80) DEFAULT NULL,
  `experience_level` varchar(80) DEFAULT NULL,
  `education_level` varchar(80) DEFAULT NULL,
  `salary_type` varchar(40) DEFAULT NULL,
  `salary_min` int DEFAULT NULL,
  `salary_max` int DEFAULT NULL,
  `salary_text` varchar(255) DEFAULT NULL,
  `work_location` varchar(255) DEFAULT NULL,
  `work_schedule` varchar(160) DEFAULT NULL,
  `headcount` varchar(80) DEFAULT NULL,
  `application_start_at` datetime DEFAULT NULL,
  `application_end_at` datetime DEFAULT NULL,
  `apply_url` varchar(512) DEFAULT NULL,
  `contact_email` varchar(255) DEFAULT NULL,
  `contact_phone` varchar(40) DEFAULT NULL,
  `visibility` varchar(20) NOT NULL DEFAULT 'PUBLIC',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING_REVIEW',
  `review_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `review_memo` varchar(1000) DEFAULT NULL,
  `pending_revision_json` json DEFAULT NULL,
  `community_post_id` bigint DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `archived_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_enterprise_job_owner` (`company_user_id`,`status`,`created_at` DESC),
  KEY `idx_enterprise_job_status` (`status`,`review_status`,`created_at` DESC),
  KEY `idx_enterprise_job_deadline` (`application_end_at`),
  KEY `idx_enterprise_job_post` (`community_post_id`),
  KEY `fk_enterprise_job_approved_by` (`approved_by`),
  KEY `fk_enterprise_job_reviewed_by` (`reviewed_by`),
  CONSTRAINT `fk_enterprise_job_approved_by` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_enterprise_job_owner` FOREIGN KEY (`company_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_enterprise_job_post` FOREIGN KEY (`community_post_id`) REFERENCES `community_post` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_enterprise_job_reviewed_by` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `interview_media_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `interview_session_id` bigint NOT NULL,
  `kind` varchar(20) NOT NULL,
  `transcript` json DEFAULT NULL,
  `metrics` json DEFAULT NULL,
  `score` int DEFAULT NULL,
  `score_detail` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_media_analysis_session` (`interview_session_id`),
  CONSTRAINT `fk_media_analysis_session` FOREIGN KEY (`interview_session_id`) REFERENCES `interview_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `legal_clause` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version_id` bigint NOT NULL COMMENT 'legal_document_version.id',
  `seq` int NOT NULL COMMENT '조항 순서 (제N조)',
  `title` varchar(200) NOT NULL COMMENT '조항 제목',
  `body` text NOT NULL COMMENT '조항 본문 (줄바꿈 = 항 1.2.3. 구분)',
  `embedding` json DEFAULT NULL COMMENT 'bge-m3 임베딩 (AI B 챗봇 RAG용, 1024차원). 후속 Phase',
  `deleted_at` datetime DEFAULT NULL COMMENT '법적 문서 조항 소프트 삭제 시각',
  PRIMARY KEY (`id`),
  KEY `idx_legal_clause_ver` (`version_id`,`seq`),
  KEY `idx_legal_clause_deleted` (`deleted_at`),
  CONSTRAINT `fk_legal_clause_ver_restrict` FOREIGN KEY (`version_id`) REFERENCES `legal_document_version` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='법적 문서 조항 (소프트 삭제로 개정 이력 보존)';
CREATE TABLE IF NOT EXISTS `legal_document_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_type` varchar(20) NOT NULL COMMENT 'TERMS | PRIVACY | MARKETING | AI_CONSENT | COPYRIGHT (LegalDocType 와 정렬)',
  `version_label` varchar(20) NOT NULL COMMENT '표시용 버전 (예: v2.4)',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | PUBLISHED',
  `summary` varchar(500) DEFAULT NULL COMMENT '개정 요약 (공지·이메일 고지에 사용 / AI 자동생성 가능)',
  `is_adverse` tinyint(1) NOT NULL DEFAULT '0' COMMENT '불리한 변경 여부 (1이면 시행 30일 전 공지 의무)',
  `effective_date` datetime DEFAULT NULL COMMENT '시행일 (DRAFT면 NULL). 공개 노출/배지 계산 기준',
  `published_at` datetime DEFAULT NULL COMMENT '게시 시각',
  `admin_id` bigint DEFAULT NULL COMMENT '작성 관리자(users.id)',
  `deleted_at` datetime DEFAULT NULL COMMENT '법적 문서 버전 소프트 삭제 시각. 조항 행은 보존',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `draft_doc_type` varchar(20) GENERATED ALWAYS AS ((case when ((`status` = _utf8mb4'DRAFT') and (`deleted_at` is null)) then `doc_type` end)) VIRTUAL COMMENT '활성 DRAFT 유일성 제약용 파생 컬럼',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legal_doctype_version` (`doc_type`,`version_label`),
  UNIQUE KEY `uk_legal_draft_one` (`draft_doc_type`),
  KEY `idx_legal_ver_type_status` (`doc_type`,`status`),
  KEY `idx_legal_ver_type_eff` (`doc_type`,`effective_date`),
  KEY `idx_legal_ver_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='법적 문서 버전 (약관/개인정보/마케팅 개정 이력)';
CREATE TABLE IF NOT EXISTS `user_activity_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(36) NOT NULL COMMENT '단일 HTTP 요청 식별자(UUID)',
  `flow_trace_id` varchar(36) DEFAULT NULL COMMENT '여러 요청에 걸친 활동 흐름 식별자',
  `user_id` bigint DEFAULT NULL COMMENT '로그인 사용자(비회원 NULL)',
  `session_id` varchar(100) DEFAULT NULL,
  `request_uri` varchar(512) NOT NULL,
  `http_method` varchar(10) NOT NULL,
  `activity_domain` varchar(30) NOT NULL DEFAULT 'GENERAL' COMMENT 'GENERAL/AUTH/ADMIN/COMMUNITY/PROFILE/SUPPORT/INTERVIEW 등',
  `activity_type` varchar(30) NOT NULL COMMENT 'API/AJAX/PAGE_VIEW/ACTION',
  `activity_code` varchar(60) DEFAULT NULL COMMENT '구체 활동 코드',
  `activity_provider` varchar(20) DEFAULT NULL COMMENT 'LOCAL/KAKAO/NAVER/GOOGLE',
  `auth_event_type` varchar(20) DEFAULT NULL COMMENT 'LOGIN/LOGOUT/LINK/UNLINK',
  `target_type` varchar(30) DEFAULT NULL,
  `target_id` varchar(100) DEFAULT NULL,
  `handler_name` varchar(200) DEFAULT NULL,
  `query_string` varchar(1000) DEFAULT NULL,
  `referer` varchar(512) DEFAULT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `response_status` int DEFAULT NULL,
  `response_time_ms` int DEFAULT NULL,
  `success` tinyint(1) DEFAULT NULL,
  `detail_summary` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_activity_log_time` (`created_at`),
  KEY `idx_user_activity_log_user` (`user_id`,`created_at`),
  KEY `idx_user_activity_log_domain` (`activity_domain`,`created_at`),
  KEY `idx_user_activity_log_request` (`request_id`),
  KEY `idx_user_activity_log_flow` (`flow_trace_id`),
  CONSTRAINT `fk_user_activity_log_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `user_chat_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `nickname` varchar(80) NOT NULL,
  `avatar_url` varchar(512) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_chat_profile_user` (`user_id`,`is_default`),
  CONSTRAINT `fk_user_chat_profile_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `user_security_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '대상 사용자(식별 가능 시)',
  `actor_user_id` bigint DEFAULT NULL COMMENT '실행 사용자(본인 변경 등)',
  `event_type` varchar(50) NOT NULL COMMENT 'FIND_ID/FIND_PASSWORD/RESET_PASSWORD/PASSWORD_CHANGE/EMAIL_VERIFY 등',
  `event_stage` varchar(20) DEFAULT NULL COMMENT 'REQUEST/ISSUE/VERIFY/COMPLETE',
  `input_identifier` varchar(255) DEFAULT NULL COMMENT '입력값(이메일/아이디)',
  `target_email` varchar(255) DEFAULT NULL,
  `success` tinyint(1) NOT NULL,
  `fail_reason` varchar(255) DEFAULT NULL,
  `detail_message` varchar(500) DEFAULT NULL,
  `request_id` varchar(36) DEFAULT NULL COMMENT 'user_activity_log.request_id 와 상관',
  `flow_trace_id` varchar(36) DEFAULT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_security_history_time` (`occurred_at`),
  KEY `idx_user_security_history_user` (`user_id`,`occurred_at`),
  KEY `idx_user_security_history_event` (`event_type`,`occurred_at`),
  KEY `fk_user_security_history_actor` (`actor_user_id`),
  CONSTRAINT `fk_user_security_history_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_security_history_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
--  리워드 이코노미 (활동 포인트/레벨/쿠폰) — patch 20260707_e_reward_economy
-- =====================================================================
CREATE TABLE IF NOT EXISTS reward_rule (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_code    VARCHAR(40)  NOT NULL COMMENT '적립 트리거. COMMUNITY_POST_CREATE/COMMUNITY_COMMENT_CREATE/APPLICATION_CASE_READY/DAILY_LOGIN/CREDIT_PURCHASE',
    name          VARCHAR(100) NOT NULL COMMENT '규칙 표시명',
    point_amount  INT          NOT NULL DEFAULT 0 COMMENT '지급 활동 포인트',
    credit_amount INT          NOT NULL DEFAULT 0 COMMENT '지급 크레딧(AI 토큰)',
    daily_cap     INT          NULL COMMENT '1일 지급 횟수 상한(NULL=무제한)',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0=미적립, 1=적립',
    description   VARCHAR(255) NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    updated_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_rule_event (event_code),
    KEY idx_reward_rule_enabled_sort (enabled, sort_order),
    KEY idx_reward_rule_updated_by (updated_by),
    CONSTRAINT fk_reward_rule_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='활동 이벤트별 리워드 적립 규칙';

CREATE TABLE IF NOT EXISTS user_level_policy (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    level               INT          NOT NULL COMMENT '레벨 번호(1부터)',
    level_name          VARCHAR(50)  NOT NULL COMMENT '레벨 표시명',
    min_point           INT          NOT NULL COMMENT '이 레벨 도달에 필요한 누적 활동 포인트',
    levelup_credit      INT          NOT NULL DEFAULT 0 COMMENT '레벨업 시 지급 크레딧',
    levelup_coupon_code VARCHAR(50)  NULL COMMENT '레벨업 시 발급 쿠폰 코드(NULL=없음)',
    benefit_note        VARCHAR(255) NULL COMMENT '레벨 혜택 설명',
    active              TINYINT(1)   NOT NULL DEFAULT 1,
    deleted_at          DATETIME     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_level_policy_level (level),
    KEY idx_user_level_policy_min_point (min_point),
    KEY idx_user_level_policy_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='활동 레벨 임계 및 레벨업 보상 정책';

CREATE TABLE IF NOT EXISTS user_reward_history (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    event_code   VARCHAR(40)  NOT NULL COMMENT '적립 이벤트 또는 LEVEL_UP/COUPON_ISSUE',
    idempotency_key VARCHAR(120) NULL COMMENT '동일 참조 이벤트 중복 적립 방지 키',
    point_delta  INT          NOT NULL DEFAULT 0,
    credit_delta INT          NOT NULL DEFAULT 0,
    level_before INT          NULL,
    level_after  INT          NULL,
    ref_type     VARCHAR(40)  NULL COMMENT '연관 엔티티. POST/COMMENT/APPLICATION_CASE/PAYMENT/LEVEL',
    ref_id       BIGINT       NULL,
    reason       VARCHAR(255) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_reward_history_idempotency (user_id, idempotency_key),
    KEY idx_user_reward_history_user (user_id, created_at),
    KEY idx_user_reward_history_event (event_code),
    CONSTRAINT fk_user_reward_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 리워드 적립/레벨업 이력';

CREATE TABLE IF NOT EXISTS coupon (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    code           VARCHAR(50)  NOT NULL COMMENT '쿠폰 코드(대문자)',
    name           VARCHAR(100) NOT NULL,
    discount_type  VARCHAR(20)  NOT NULL COMMENT 'CREDIT=크레딧지급 / PERCENT=결제%할인 / AMOUNT=정액할인(원)',
    discount_value INT          NOT NULL DEFAULT 0 COMMENT 'PERCENT면 %, AMOUNT면 원, CREDIT이면 크레딧 수',
    min_purchase   INT          NOT NULL DEFAULT 0 COMMENT '최소 결제 금액(원). 할인형에 적용',
    valid_from     DATETIME     NULL,
    valid_until    DATETIME     NULL,
    max_issue      INT          NULL COMMENT '총 발급 상한(NULL=무제한)',
    issued_count   INT          NOT NULL DEFAULT 0,
    enabled        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_code (code),
    KEY idx_coupon_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='쿠폰 정의';

CREATE TABLE IF NOT EXISTS user_coupon (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    coupon_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    code       VARCHAR(50) NOT NULL COMMENT '발급 시점 코드 스냅샷',
    status     VARCHAR(20) NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED/USED/EXPIRED',
    issued_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at    DATETIME    NULL,
    order_ref  BIGINT      NULL COMMENT '사용된 결제/충전 참조',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_coupon_user_status (user_id, status),
    KEY idx_user_coupon_coupon (coupon_id),
    CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 발급 쿠폰';

-- =====================================================================
--  관리자/직원 등급·급여 — patch 20260707_a_admin_staff_grade
-- =====================================================================
CREATE TABLE IF NOT EXISTS admin_staff_grade (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT '관리자/직원 사용자 ID',
    department     VARCHAR(60)  NULL COMMENT '부서',
    seniority      VARCHAR(30)  NULL COMMENT '연차 구분. JUNIOR/MID/SENIOR/LEAD/PRINCIPAL',
    job_tier       VARCHAR(30)  NULL COMMENT '직군 티어',
    pay_band       VARCHAR(30)  NULL COMMENT '급여 밴드',
    job_grade      VARCHAR(30)  NULL COMMENT '직급/등급',
    pay_step       VARCHAR(30)  NULL COMMENT '호봉/스텝',
    base_salary    INT          NULL COMMENT '기본 연봉(원). 민감정보',
    currency       VARCHAR(10)  NOT NULL DEFAULT 'KRW',
    effective_date DATE         NULL COMMENT '적용 시작일',
    memo           VARCHAR(255) NULL,
    updated_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_staff_grade_user (user_id),
    KEY idx_admin_staff_grade_dept (department),
    KEY idx_admin_staff_grade_updated_by (updated_by),
    CONSTRAINT fk_admin_staff_grade_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_staff_grade_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자/직원 조직 등급 및 급여';

CREATE TABLE IF NOT EXISTS admin_staff_grade_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    old_values_json JSON         NULL COMMENT '변경 전 스냅샷',
    new_values_json JSON         NULL COMMENT '변경 후 스냅샷',
    changed_by      BIGINT       NULL,
    source          VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/EXCEL',
    memo            VARCHAR(255) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_staff_grade_history_user (user_id, created_at),
    KEY idx_admin_staff_grade_history_changed_by (changed_by),
    CONSTRAINT fk_staff_grade_hist_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_grade_hist_changed_by FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자/직원 등급·급여 변경 이력';

-- 리워드 기본 시드(적립 규칙/레벨 정책/대표 쿠폰) — 신규 설치 시 즉시 동작하도록 편입
INSERT IGNORE INTO reward_rule (event_code, name, point_amount, credit_amount, daily_cap, enabled, description, sort_order) VALUES
    ('COMMUNITY_POST_CREATE',    '커뮤니티 글 작성',   10, 0,    5, 1, '커뮤니티 게시글 작성 시 활동 포인트 적립(1일 5회)', 1),
    ('COMMUNITY_COMMENT_CREATE', '커뮤니티 댓글 작성',  3, 0,   10, 1, '댓글 작성 시 활동 포인트 적립(1일 10회)',          2),
    ('APPLICATION_CASE_READY',   '지원 건 분석 완료',  30, 1, NULL, 1, '지원 건 자동 분석 완료 시 포인트+크레딧 적립',      3),
    ('DAILY_LOGIN',              '일일 첫 로그인',      5, 0,    1, 1, '하루 첫 로그인 시 활동 포인트 적립(1일 1회)',        4),
    ('CREDIT_PURCHASE',          '크레딧 구매 페이백', 50, 0, NULL, 1, '크레딧 구매 확정 시 활동 포인트 페이백',            5);

INSERT IGNORE INTO user_level_policy (level, level_name, min_point, levelup_credit, levelup_coupon_code, benefit_note, active) VALUES
    (1, '새싹',   0,     0, NULL,             '기본 레벨',                   1),
    (2, '성장',   100,   5, NULL,             '레벨업 크레딧 5 지급',        1),
    (3, '숙련',   300,  10, NULL,             '레벨업 크레딧 10 지급',       1),
    (4, '전문가', 700,  20, 'LEVELUP_PRO',    '크레딧 20 + 전문가 쿠폰 지급', 1),
    (5, '마스터', 1500, 50, 'LEVELUP_MASTER', '크레딧 50 + 마스터 쿠폰 지급', 1);

INSERT IGNORE INTO coupon (code, name, discount_type, discount_value, min_purchase, max_issue, enabled) VALUES
    ('WELCOME10',      '가입 환영 10% 할인',   'PERCENT', 10, 0, NULL, 1),
    ('LEVELUP_PRO',    '전문가 달성 크레딧 20', 'CREDIT',  20, 0, NULL, 1),
    ('LEVELUP_MASTER', '마스터 달성 크레딧 50', 'CREDIT',  50, 0, NULL, 1);

-- ── MFA(2단계 인증) — patch 20260708_mfa_2fa 동기화(신규 환경이 schema.sql 만으로 완전 구성되도록) ──
CREATE TABLE IF NOT EXISTS user_mfa_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'MFA 설정 ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'MFA 활성화 여부',
    verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '최초 코드 검증 완료 여부',
    mfa_type VARCHAR(20) NOT NULL DEFAULT 'TOTP' COMMENT 'MFA 방식(TOTP/PUSH)',
    secret_key_encrypted TEXT NULL COMMENT '암호화된 TOTP 시크릿 키',
    device_name VARCHAR(120) NULL COMMENT '등록한 인증 기기 이름',
    push_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '모바일 승인형 인증 허용 여부',
    last_verified_at DATETIME NULL COMMENT '마지막 설정 검증 시각',
    last_used_at DATETIME NULL COMMENT '마지막 MFA 로그인 성공 시각',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    UNIQUE KEY uk_user_mfa_setting_user (user_id),
    CONSTRAINT fk_user_mfa_setting_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) COMMENT='회원별 2단계 인증 설정';

CREATE TABLE IF NOT EXISTS mfa_challenge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'MFA 로그인 챌린지 ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    challenge_token VARCHAR(100) NOT NULL COMMENT '로그인 1차 인증 후 발급되는 임시 토큰',
    challenge_type VARCHAR(30) NOT NULL DEFAULT 'LOGIN' COMMENT '챌린지 용도(LOGIN 등)',
    delivery_type VARCHAR(30) NOT NULL DEFAULT 'TOTP_OR_PUSH' COMMENT '인증 방식(TOTP/PUSH/TOTP_OR_PUSH)',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태(PENDING/APPROVED/DENIED/VERIFIED/EXPIRED)',
    expires_at DATETIME NOT NULL COMMENT '만료 시각',
    approved_at DATETIME NULL COMMENT '모바일 승인 시각',
    verified_at DATETIME NULL COMMENT '코드 또는 승인 검증 완료 시각',
    ip_address VARCHAR(45) NULL COMMENT '로그인 요청 IP',
    user_agent VARCHAR(500) NULL COMMENT '로그인 요청 User-Agent',
    device_name VARCHAR(120) NULL COMMENT '로그인 요청 또는 승인 기기 이름',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    UNIQUE KEY uk_mfa_challenge_token (challenge_token),
    KEY idx_mfa_challenge_user_status (user_id, status, expires_at),
    CONSTRAINT fk_mfa_challenge_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) COMMENT='로그인 2단계 인증 챌린지';

CREATE TABLE IF NOT EXISTS mfa_backup_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'MFA 백업 코드 ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    code_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 해시 처리된 백업 코드',
    used TINYINT(1) NOT NULL DEFAULT 0 COMMENT '사용 여부',
    used_at DATETIME NULL COMMENT '사용 시각',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    KEY idx_mfa_backup_code_user_used (user_id, used),
    CONSTRAINT fk_mfa_backup_code_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) COMMENT='MFA 분실 대비 백업 코드';

CREATE TABLE IF NOT EXISTS mfa_policy (
    id BIGINT PRIMARY KEY COMMENT 'MFA 정책 ID(단일 정책은 1)',
    require_admins TINYINT(1) NOT NULL DEFAULT 0 COMMENT '관리자 계정 MFA 설정 유도 여부',
    allow_backup_code TINYINT(1) NOT NULL DEFAULT 1 COMMENT '백업 코드 로그인 허용 여부',
    allow_push_approval TINYINT(1) NOT NULL DEFAULT 1 COMMENT '모바일 승인형 인증 허용 여부',
    updated_by BIGINT NULL COMMENT '마지막 수정 관리자 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    CONSTRAINT fk_mfa_policy_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) COMMENT='MFA 운영 정책';

SET FOREIGN_KEY_CHECKS = 1;
