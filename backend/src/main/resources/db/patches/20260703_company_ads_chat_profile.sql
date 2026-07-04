ALTER TABLE users
    ADD COLUMN login_id VARCHAR(60) NULL COMMENT '이메일 외 사용자가 선택한 로그인/표시용 아이디' AFTER email_verified,
    ADD COLUMN phone_number VARCHAR(40) NULL COMMENT '취업 연계 연락을 위한 휴대전화 번호' AFTER login_id,
    ADD COLUMN phone_verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '휴대전화 인증 완료 여부' AFTER phone_number,
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'PERSONAL' COMMENT 'PERSONAL/EMPLOYER 기업 계정 전환 상태' AFTER user_type,
    ADD COLUMN enterprise_trusted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '기업 공고 등록/수정 정책에서 신뢰 기업 여부' AFTER account_type,
    ADD UNIQUE KEY uk_users_login_id (login_id),
    ADD KEY idx_users_account_type (account_type, enterprise_trusted);

ALTER TABLE user_profile
    ADD COLUMN job_preferences JSON NULL COMMENT '희망 지역/연봉/근무형태/고용형태 등 구직 조건' AFTER portfolio_links,
    ADD COLUMN personal_info JSON NULL COMMENT '닉네임, 연락 가능 시간, 병역/보훈/장애 등 취업 연계 세부 정보' AFTER job_preferences,
    ADD COLUMN activities JSON NULL COMMENT '대외활동, 수상, 교육, 봉사, 링크 등 정형 경력 외 활동' AFTER personal_info,
    ADD COLUMN account_links JSON NULL COMMENT '카카오/네이버/구글 등 계정 연결 표시용 스냅샷' AFTER activities,
    ADD COLUMN chat_profiles JSON NULL COMMENT '채팅에서 사용할 여러 닉네임/방 전용 프로필 후보' AFTER account_links;

CREATE TABLE IF NOT EXISTS enterprise_account_application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    company_name VARCHAR(160) NOT NULL,
    business_number VARCHAR(80) NULL,
    representative_name VARCHAR(100) NULL,
    contact_name VARCHAR(100) NULL,
    contact_email VARCHAR(255) NULL,
    contact_phone VARCHAR(40) NULL,
    website_url VARCHAR(512) NULL,
    industry VARCHAR(120) NULL,
    employee_count VARCHAR(60) NULL,
    evidence_file_url VARCHAR(512) NULL,
    requested_policy_json JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_memo VARCHAR(1000) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enterprise_application_user (user_id, created_at DESC),
    KEY idx_enterprise_application_status (status, created_at DESC),
    CONSTRAINT fk_enterprise_application_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_enterprise_application_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS enterprise_job_policy (
    user_id BIGINT NOT NULL,
    trusted TINYINT(1) NOT NULL DEFAULT 0,
    create_requires_review TINYINT(1) NOT NULL DEFAULT 1,
    edit_requires_review TINYINT(1) NOT NULL DEFAULT 1,
    max_active_posts INT NOT NULL DEFAULT 5,
    updated_by BIGINT NULL,
    update_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_enterprise_job_policy_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_enterprise_job_policy_admin FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS enterprise_job_posting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_user_id BIGINT NOT NULL,
    company_name VARCHAR(160) NOT NULL,
    title VARCHAR(180) NOT NULL,
    position_title VARCHAR(160) NOT NULL,
    job_category VARCHAR(120) NULL,
    specialties_json JSON NULL,
    duties MEDIUMTEXT NOT NULL,
    qualifications MEDIUMTEXT NULL,
    preferred MEDIUMTEXT NULL,
    benefits MEDIUMTEXT NULL,
    employment_type VARCHAR(80) NULL,
    experience_level VARCHAR(80) NULL,
    education_level VARCHAR(80) NULL,
    salary_type VARCHAR(40) NULL,
    salary_min INT NULL,
    salary_max INT NULL,
    salary_text VARCHAR(255) NULL,
    work_location VARCHAR(255) NULL,
    work_schedule VARCHAR(160) NULL,
    headcount VARCHAR(80) NULL,
    application_start_at DATETIME NULL,
    application_end_at DATETIME NULL,
    apply_url VARCHAR(512) NULL,
    contact_email VARCHAR(255) NULL,
    contact_phone VARCHAR(40) NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_memo VARCHAR(1000) NULL,
    pending_revision_json JSON NULL,
    community_post_id BIGINT NULL,
    approved_by BIGINT NULL,
    approved_at DATETIME NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    archived_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enterprise_job_owner (company_user_id, status, created_at DESC),
    KEY idx_enterprise_job_status (status, review_status, created_at DESC),
    KEY idx_enterprise_job_deadline (application_end_at),
    KEY idx_enterprise_job_post (community_post_id),
    CONSTRAINT fk_enterprise_job_owner FOREIGN KEY (company_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_enterprise_job_post FOREIGN KEY (community_post_id) REFERENCES community_post (id) ON DELETE SET NULL,
    CONSTRAINT fk_enterprise_job_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_enterprise_job_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS admin_ad_campaign (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NULL,
    surface VARCHAR(20) NOT NULL DEFAULT 'WEB',
    placement VARCHAR(80) NOT NULL DEFAULT 'GLOBAL_TOP',
    creative_type VARCHAR(20) NOT NULL DEFAULT 'BANNER',
    image_url VARCHAR(512) NULL,
    target_url VARCHAR(512) NULL,
    visible_to_plans_json JSON NULL,
    starts_at DATETIME NULL,
    ends_at DATETIME NULL,
    priority INT NOT NULL DEFAULT 100,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_ad_surface_active (surface, active, priority),
    KEY idx_admin_ad_schedule (starts_at, ends_at),
    CONSTRAINT fk_admin_ad_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_ad_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ad_impression_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    surface VARCHAR(20) NOT NULL,
    event_type VARCHAR(20) NOT NULL DEFAULT 'IMPRESSION',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ad_impression_campaign (campaign_id, created_at DESC),
    KEY idx_ad_impression_user (user_id, created_at DESC),
    CONSTRAINT fk_ad_impression_campaign FOREIGN KEY (campaign_id) REFERENCES admin_ad_campaign (id) ON DELETE CASCADE,
    CONSTRAINT fk_ad_impression_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE collaboration_conversation
    ADD COLUMN profile_image_url VARCHAR(512) NULL AFTER description,
    ADD COLUMN join_policy VARCHAR(30) NOT NULL DEFAULT 'DEFAULT' AFTER max_members,
    ADD COLUMN invite_policy VARCHAR(30) NOT NULL DEFAULT 'OWNER_AND_MANAGERS' AFTER join_policy,
    ADD COLUMN anonymous_allowed TINYINT(1) NOT NULL DEFAULT 0 AFTER invite_policy,
    ADD COLUMN anonymous_only TINYINT(1) NOT NULL DEFAULT 0 AFTER anonymous_allowed,
    ADD COLUMN room_profile_required TINYINT(1) NOT NULL DEFAULT 0 AFTER anonymous_only,
    ADD COLUMN settings_json JSON NULL AFTER room_profile_required;

ALTER TABLE collaboration_conversation_member
    ADD COLUMN display_name VARCHAR(80) NULL AFTER muted,
    ADD COLUMN avatar_url VARCHAR(512) NULL AFTER display_name,
    ADD COLUMN anonymous TINYINT(1) NOT NULL DEFAULT 0 AFTER avatar_url,
    ADD COLUMN permissions_json JSON NULL AFTER anonymous,
    ADD COLUMN room_profile_json JSON NULL AFTER permissions_json,
    ADD COLUMN removed_by BIGINT NULL AFTER left_at,
    ADD CONSTRAINT fk_collab_conversation_member_removed_by FOREIGN KEY (removed_by) REFERENCES users (id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS collaboration_conversation_ban (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    banned_by BIGINT NULL,
    reason VARCHAR(500) NULL,
    banned_until DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_conversation_ban_user (conversation_id, user_id),
    CONSTRAINT fk_collab_conversation_ban_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_ban_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_ban_admin FOREIGN KEY (banned_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_chat_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    nickname VARCHAR(80) NOT NULL,
    avatar_url VARCHAR(512) NULL,
    description VARCHAR(255) NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_chat_profile_user (user_id, is_default),
    CONSTRAINT fk_user_chat_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
