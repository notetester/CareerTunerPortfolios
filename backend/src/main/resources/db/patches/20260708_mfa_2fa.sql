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

INSERT INTO mfa_policy (id, require_admins, allow_backup_code, allow_push_approval)
VALUES (1, 0, 1, 1)
ON DUPLICATE KEY UPDATE id = id;
