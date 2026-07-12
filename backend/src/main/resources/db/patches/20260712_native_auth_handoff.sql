-- 카카오/네이버/구글 OAuth 제공자 응답을 앱 PKCE verifier와 안전하게 교환한다.
-- verifier 검증 전에는 회원/소셜 계정을 만들지 않고 access/refresh token과 handoffCode 원문도 저장하지 않는다.
-- 배포 전 후보 스키마(user_id/consumed_at)를 적용한 환경은 최대 3분짜리 미완료 handoff만 폐기하고 교체한다.
SET @native_handoff_legacy_columns = (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'native_auth_handoff'
       AND COLUMN_NAME IN ('user_id', 'consumed_at')
);
SET @native_handoff_table_exists = (
    SELECT COUNT(*)
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'native_auth_handoff'
);
SET @native_handoff_email_verified_columns = (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'native_auth_handoff'
       AND COLUMN_NAME = 'email_verified'
);
SET @native_handoff_upgrade_sql = IF(
    @native_handoff_legacy_columns > 0
        OR (@native_handoff_table_exists > 0 AND @native_handoff_email_verified_columns = 0),
    'DROP TABLE native_auth_handoff',
    'SELECT 1'
);
PREPARE native_handoff_upgrade_stmt FROM @native_handoff_upgrade_sql;
EXECUTE native_handoff_upgrade_stmt;
DEALLOCATE PREPARE native_handoff_upgrade_stmt;

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

SELECT
    COUNT(*) AS native_auth_handoff_table_count
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'native_auth_handoff';
