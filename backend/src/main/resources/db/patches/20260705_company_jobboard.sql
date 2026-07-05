-- 기업 계정 파이프라인 + 채용공고 게시판 (W1)
-- 1) company_application        : 기업 계정 전환 신청(USER → COMPANY). PENDING 1건 제한은 서비스에서 검증.
-- 2) user_role_change_history   : role 변경 감사 이력(기업 승인 등 모든 role 전환 시 기록).
-- 3) company_profile            : 승인된 기업 프로필(1:1). trust_grade 가 공고 승인 정책의 입력값.
-- 4) company_job_posting        : 기업 채용공고 게시판 본체(사람인식 상세 필드).
--    * 기존 job_posting(지원 건 내부 공고 원문)과는 별개 도메인 — 혼동 금지.
-- 5) company_job_posting_revision : 게시 중 공고의 수정 검토용 변경본(JSON payload).
-- 6) admin_system_policy 에 JOB_POSTING_REVIEW_POLICY seed(신뢰등급별 등록/수정 검토 필요 여부).

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

-- 신뢰등급별 공고 검토 정책 seed. AdminPolicyController(운영 정책 관리 화면)로 수정한다.
--   BASIC   : 등록/수정 모두 사전 검토
--   VERIFIED: 등록만 사전 검토, 수정은 즉시 반영
--   PARTNER : 등록/수정 모두 즉시 반영
INSERT IGNORE INTO admin_system_policy (policy_code, display_name, description, config_json, schedule_type, active)
VALUES ('JOB_POSTING_REVIEW_POLICY', '채용공고 검토 정책', '기업 신뢰등급별 공고 등록/수정 시 관리자 사전 검토 필요 여부',
        JSON_OBJECT(
            'BASIC',    JSON_OBJECT('createRequiresReview', TRUE,  'updateRequiresReview', TRUE),
            'VERIFIED', JSON_OBJECT('createRequiresReview', TRUE,  'updateRequiresReview', FALSE),
            'PARTNER',  JSON_OBJECT('createRequiresReview', FALSE, 'updateRequiresReview', FALSE)
        ),
        'MANUAL', 1);
