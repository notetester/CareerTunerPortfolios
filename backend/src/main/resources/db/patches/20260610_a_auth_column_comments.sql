-- A auth/admin-user column comments.
-- Applies descriptive MySQL comments to login, token, dormant-account, and audit columns.

ALTER TABLE users
    MODIFY COLUMN email VARCHAR(255) NOT NULL COMMENT '로그인 식별자로 사용하는 회원 이메일',
    MODIFY COLUMN password VARCHAR(255) NULL COMMENT 'BCrypt로 암호화한 비밀번호 해시. 소셜 전용 계정은 NULL 가능',
    MODIFY COLUMN password_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '비밀번호 로그인 사용 여부. 소셜 전용 계정은 0',
    MODIFY COLUMN email_verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '이메일 인증 완료 여부',
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '회원 권한. USER 또는 ADMIN',
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '회원 상태. ACTIVE/DORMANT/BLOCKED/DELETED',
    MODIFY COLUMN last_login_at DATETIME NULL COMMENT '마지막 로그인 성공 시각',
    MODIFY COLUMN dormant_at DATETIME NULL COMMENT '휴면 계정으로 전환된 시각',
    MODIFY COLUMN blocked_reason VARCHAR(255) NULL COMMENT '관리자가 회원을 차단한 사유',
    MODIFY COLUMN blocked_until DATETIME NULL COMMENT '기간 차단 만료 시각. NULL이면 무기한 또는 미차단',
    MODIFY COLUMN deleted_at DATETIME NULL COMMENT '회원 탈퇴 또는 삭제 처리 시각',
    MODIFY COLUMN status_changed_at DATETIME NULL COMMENT '회원 상태가 마지막으로 변경된 시각',
    MODIFY COLUMN status_changed_by BIGINT NULL COMMENT '회원 상태를 변경한 관리자 ID. 시스템 변경이면 NULL',
    MODIFY COLUMN failed_login_count INT NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수',
    MODIFY COLUMN last_failed_login_at DATETIME NULL COMMENT '마지막 로그인 실패 시각',
    COMMENT = '회원 기본 정보와 로그인/권한/상태 관리 정보';

ALTER TABLE refresh_token
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '토큰을 발급받은 회원 ID',
    MODIFY COLUMN token VARCHAR(512) NOT NULL COMMENT '저장된 JWT refresh token 값',
    MODIFY COLUMN expired_at DATETIME NOT NULL COMMENT 'refresh token 만료 시각',
    MODIFY COLUMN revoked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '토큰 폐기 여부',
    MODIFY COLUMN revoked_at DATETIME NULL COMMENT '토큰이 폐기된 시각',
    MODIFY COLUMN ip_address VARCHAR(45) NULL COMMENT '토큰 발급 요청 IP 주소',
    MODIFY COLUMN user_agent VARCHAR(500) NULL COMMENT '토큰 발급 요청 User-Agent',
    COMMENT = 'JWT refresh token 저장 및 세션 감사 정보';

ALTER TABLE user_login_history
    MODIFY COLUMN user_id BIGINT NULL COMMENT '로그인 이벤트 대상 회원 ID. 실패로 회원 식별이 안 되면 NULL',
    MODIFY COLUMN event_type VARCHAR(20) NOT NULL COMMENT '인증 이벤트 유형. LOGIN/LOGOUT/REFRESH',
    MODIFY COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL' COMMENT '인증 제공자. LOCAL/KAKAO/NAVER/GOOGLE',
    MODIFY COLUMN login_method VARCHAR(20) NULL COMMENT '로그인 방식. EMAIL/OAUTH/REFRESH_TOKEN',
    MODIFY COLUMN login_identifier VARCHAR(255) NULL COMMENT '사용자가 입력한 로그인 식별자. 보통 이메일',
    MODIFY COLUMN success TINYINT(1) NOT NULL COMMENT '인증 성공 여부',
    MODIFY COLUMN fail_reason VARCHAR(50) NULL COMMENT '실패 사유. USER_NOT_FOUND/WRONG_PASSWORD/BLOCKED 등',
    MODIFY COLUMN ip_address VARCHAR(45) NULL COMMENT '요청 IP 주소',
    MODIFY COLUMN user_agent VARCHAR(500) NULL COMMENT '요청 User-Agent',
    MODIFY COLUMN request_uri VARCHAR(255) NULL COMMENT '인증 요청 URI',
    COMMENT = '로그인, 로그아웃, 토큰 갱신 감사 로그';

ALTER TABLE user_status_history
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '상태가 변경된 회원 ID',
    MODIFY COLUMN actor_user_id BIGINT NULL COMMENT '상태를 변경한 관리자 ID. 시스템 자동 변경이면 NULL',
    MODIFY COLUMN previous_status VARCHAR(20) NULL COMMENT '변경 전 회원 상태',
    MODIFY COLUMN new_status VARCHAR(20) NOT NULL COMMENT '변경 후 회원 상태',
    MODIFY COLUMN reason VARCHAR(255) NULL COMMENT '상태 변경 사유',
    MODIFY COLUMN memo TEXT NULL COMMENT '관리자 내부 메모',
    MODIFY COLUMN blocked_until DATETIME NULL COMMENT '차단 만료 시각. 차단 상태가 아니거나 무기한이면 NULL',
    COMMENT = '회원 상태 변경 이력';
