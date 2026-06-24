-- A/Admin 운영 고도화: 관리자 액션 로그, Super 관리자 권한, 운영 정책
-- 실행: mysql -h <host> -u <user> -p <db> < 20260624_a_admin_ops_superadmin.sql

ALTER TABLE users
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'
    COMMENT '회원 권한. USER/ADMIN/SUPER_ADMIN';

CREATE TABLE IF NOT EXISTS admin_action_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '관리자 액션 로그 ID',
    actor_user_id   BIGINT NULL COMMENT '액션을 수행한 관리자 ID',
    target_user_id  BIGINT NULL COMMENT '대상 회원/관리자 ID',
    action_type     VARCHAR(80) NOT NULL COMMENT '액션 유형',
    target_type     VARCHAR(80) NULL COMMENT '대상 유형',
    before_value    JSON NULL COMMENT '변경 전 값',
    after_value     JSON NULL COMMENT '변경 후 값',
    reason          VARCHAR(500) NULL COMMENT '처리 사유',
    ip_address      VARCHAR(64) NULL COMMENT '요청 IP',
    user_agent      VARCHAR(500) NULL COMMENT '요청 User-Agent',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '기록 시각',
    KEY idx_admin_action_actor (actor_user_id, created_at),
    KEY idx_admin_action_target (target_user_id, created_at),
    KEY idx_admin_action_type (action_type, created_at),
    CONSTRAINT fk_admin_action_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_action_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE SET NULL
) COMMENT='관리자 기능 실행 감사 로그';

CREATE TABLE IF NOT EXISTS admin_permission_policy (
    permission_code VARCHAR(80) PRIMARY KEY COMMENT '권한 코드',
    display_name    VARCHAR(100) NOT NULL COMMENT '권한명',
    description     VARCHAR(500) NULL COMMENT '권한 설명',
    active          TINYINT(1) NOT NULL DEFAULT 1 COMMENT '활성 여부',
    created_by      BIGINT NULL COMMENT '생성 관리자 ID',
    updated_by      BIGINT NULL COMMENT '수정 관리자 ID',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    KEY idx_admin_perm_active (active),
    CONSTRAINT fk_admin_perm_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_perm_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) COMMENT='관리자 개별 권한 정책';

CREATE TABLE IF NOT EXISTS admin_permission_group (
    group_code   VARCHAR(80) PRIMARY KEY COMMENT '권한 그룹 코드',
    display_name VARCHAR(100) NOT NULL COMMENT '권한 그룹명',
    description  VARCHAR(500) NULL COMMENT '권한 그룹 설명',
    active       TINYINT(1) NOT NULL DEFAULT 1 COMMENT '활성 여부',
    created_by   BIGINT NULL COMMENT '생성 관리자 ID',
    updated_by   BIGINT NULL COMMENT '수정 관리자 ID',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    KEY idx_admin_perm_group_active (active),
    CONSTRAINT fk_admin_perm_group_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_perm_group_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) COMMENT='관리자 권한 그룹';

CREATE TABLE IF NOT EXISTS admin_permission_group_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '권한 그룹 항목 ID',
    group_code      VARCHAR(80) NOT NULL COMMENT '권한 그룹 코드',
    permission_code VARCHAR(80) NOT NULL COMMENT '권한 코드',
    created_by      BIGINT NULL COMMENT '추가 관리자 ID',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '추가일',
    UNIQUE KEY uk_admin_perm_group_item (group_code, permission_code),
    CONSTRAINT fk_admin_perm_group_item_group FOREIGN KEY (group_code) REFERENCES admin_permission_group (group_code) ON DELETE CASCADE,
    CONSTRAINT fk_admin_perm_group_item_perm FOREIGN KEY (permission_code) REFERENCES admin_permission_policy (permission_code) ON DELETE CASCADE,
    CONSTRAINT fk_admin_perm_group_item_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) COMMENT='권한 그룹에 포함된 개별 권한';

CREATE TABLE IF NOT EXISTS admin_user_permission (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '관리자 직접 권한 ID',
    user_id         BIGINT NOT NULL COMMENT '관리자 회원 ID',
    permission_code VARCHAR(80) NOT NULL COMMENT '권한 코드',
    granted_by      BIGINT NULL COMMENT '부여 관리자 ID',
    granted_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '부여일',
    revoked_at      DATETIME NULL COMMENT '회수일',
    UNIQUE KEY uk_admin_user_perm_active (user_id, permission_code, revoked_at),
    KEY idx_admin_user_perm_user (user_id, revoked_at),
    CONSTRAINT fk_admin_user_perm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_user_perm_code FOREIGN KEY (permission_code) REFERENCES admin_permission_policy (permission_code) ON DELETE CASCADE,
    CONSTRAINT fk_admin_user_perm_granted_by FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL
) COMMENT='관리자에게 직접 부여된 권한';

CREATE TABLE IF NOT EXISTS admin_user_group (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '관리자 그룹 소속 ID',
    user_id     BIGINT NOT NULL COMMENT '관리자 회원 ID',
    group_code  VARCHAR(80) NOT NULL COMMENT '권한 그룹 코드',
    granted_by  BIGINT NULL COMMENT '배정 관리자 ID',
    granted_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '배정일',
    revoked_at  DATETIME NULL COMMENT '해제일',
    UNIQUE KEY uk_admin_user_group_active (user_id, group_code, revoked_at),
    KEY idx_admin_user_group_user (user_id, revoked_at),
    CONSTRAINT fk_admin_user_group_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_user_group_group FOREIGN KEY (group_code) REFERENCES admin_permission_group (group_code) ON DELETE CASCADE,
    CONSTRAINT fk_admin_user_group_granted_by FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL
) COMMENT='관리자 권한 그룹 소속';

CREATE TABLE IF NOT EXISTS admin_permission_audit (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '권한 변경 이력 ID',
    actor_user_id   BIGINT NULL COMMENT '처리 관리자 ID',
    target_user_id  BIGINT NULL COMMENT '대상 관리자 ID',
    action_type     VARCHAR(80) NOT NULL COMMENT '권한 액션 유형',
    permission_code VARCHAR(80) NULL COMMENT '권한 코드',
    group_code      VARCHAR(80) NULL COMMENT '권한 그룹 코드',
    reason          VARCHAR(500) NULL COMMENT '처리 사유',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '기록 시각',
    KEY idx_admin_perm_audit_target (target_user_id, created_at),
    KEY idx_admin_perm_audit_actor (actor_user_id, created_at),
    CONSTRAINT fk_admin_perm_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_perm_audit_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE SET NULL
) COMMENT='관리자 권한 변경 감사 이력';

CREATE TABLE IF NOT EXISTS admin_system_policy (
    policy_code             VARCHAR(80) PRIMARY KEY COMMENT '운영 정책 코드',
    display_name            VARCHAR(100) NOT NULL COMMENT '정책명',
    description             VARCHAR(500) NULL COMMENT '정책 설명',
    config_json             JSON NULL COMMENT '정책 설정 JSON',
    schedule_type           VARCHAR(30) NOT NULL DEFAULT 'MANUAL' COMMENT '실행 방식. MANUAL/DAILY/WEEKLY/MONTHLY',
    schedule_interval_hours INT NULL COMMENT '반복 실행 간격(시간)',
    schedule_day_of_month   INT NULL COMMENT '월간 실행일',
    schedule_time           VARCHAR(10) NULL COMMENT '실행 시각 HH:mm',
    active                  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '정책 활성 여부',
    last_run_at             DATETIME NULL COMMENT '마지막 실행 시각',
    last_run_status         VARCHAR(20) NULL COMMENT '마지막 실행 상태',
    last_run_message        VARCHAR(1000) NULL COMMENT '마지막 실행 메시지',
    updated_by              BIGINT NULL COMMENT '마지막 수정 관리자 ID',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    KEY idx_admin_system_policy_active (active),
    CONSTRAINT fk_admin_system_policy_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) COMMENT='관리자 운영 정책';

INSERT IGNORE INTO admin_permission_policy (permission_code, display_name, description)
VALUES
('USER_READ', '회원 조회', '회원 목록과 상세 컨텍스트 조회'),
('USER_STATUS_WRITE', '회원 상태 변경', '회원 차단/휴면/해제 등 상태 변경'),
('PROFILE_READ', '프로필 조회', '사용자 프로필과 입력 상태 조회'),
('CONSENT_READ', '동의 조회', 'AI_DATA 등 동의/철회 이력 조회'),
('AI_USAGE_READ', 'AI 사용 이력 조회', 'AI 사용 로그와 실패 이력 조회'),
('SECURITY_LOG_READ', '보안 로그 조회', '로그인/이메일 인증/세션 이력 조회'),
('POLICY_MANAGE', '운영 정책 관리', '운영 정책 수정 및 즉시 실행'),
('ADMIN_PERMISSION_MANAGE', '관리자 권한 관리', '관리자 승격, 권한, 그룹 관리');

INSERT IGNORE INTO admin_permission_group (group_code, display_name, description)
VALUES
('A_PART_OPERATOR', 'A파트 운영자', '회원/프로필/동의/AI 사용 이력 조회 권한'),
('SECURITY_OPERATOR', '보안 운영자', '로그인 감사와 회원 상태 조치 권한'),
('SUPER_ADMIN_GROUP', '슈퍼 관리자 그룹', '관리자 권한과 운영 정책 관리 권한');

INSERT IGNORE INTO admin_permission_group_item (group_code, permission_code)
VALUES
('A_PART_OPERATOR', 'USER_READ'),
('A_PART_OPERATOR', 'PROFILE_READ'),
('A_PART_OPERATOR', 'CONSENT_READ'),
('A_PART_OPERATOR', 'AI_USAGE_READ'),
('SECURITY_OPERATOR', 'USER_READ'),
('SECURITY_OPERATOR', 'USER_STATUS_WRITE'),
('SECURITY_OPERATOR', 'SECURITY_LOG_READ'),
('SUPER_ADMIN_GROUP', 'ADMIN_PERMISSION_MANAGE'),
('SUPER_ADMIN_GROUP', 'POLICY_MANAGE'),
('SUPER_ADMIN_GROUP', 'USER_READ'),
('SUPER_ADMIN_GROUP', 'USER_STATUS_WRITE'),
('SUPER_ADMIN_GROUP', 'SECURITY_LOG_READ');

INSERT IGNORE INTO admin_system_policy (policy_code, display_name, description, config_json, schedule_type, active)
VALUES
('DORMANT_ACCOUNT', '휴면 계정 전환', '장기 미접속 회원을 휴면 상태로 전환하는 기준', JSON_OBJECT('inactiveDays', 365), 'DAILY', 0),
('FAILED_LOGIN_LOCK', '로그인 실패 자동 잠금', '연속 로그인 실패 시 계정 잠금 기준', JSON_OBJECT('maxFailedCount', 5, 'lockMinutes', 10), 'MANUAL', 1),
('EMAIL_TOKEN_CLEANUP', '이메일 토큰 정리', '만료된 이메일 인증/비밀번호 재설정 토큰 정리', JSON_OBJECT('retentionDays', 30), 'DAILY', 0),
('AI_USAGE_RETENTION', 'AI 사용 로그 보관', 'AI 사용 이력 보관 기간 정책', JSON_OBJECT('retentionDays', 365), 'MONTHLY', 0);
