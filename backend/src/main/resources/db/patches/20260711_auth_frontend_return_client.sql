-- 이메일 인증 결과를 요청이 시작된 프런트엔드로 돌려보내기 위한 named client를 저장한다.
-- URL 자체는 저장하지 않고 서버 설정에 매핑되는 primary/sites 키만 저장한다.
SET @frontend_client_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'email_verification'
      AND COLUMN_NAME = 'frontend_client'
);
SET @frontend_client_sql = IF(
    @frontend_client_exists = 0,
    'ALTER TABLE email_verification ADD COLUMN frontend_client VARCHAR(32) NOT NULL DEFAULT ''primary'' COMMENT ''결과를 돌려보낼 named frontend client'' AFTER purpose',
    'SELECT 1'
);
PREPARE frontend_client_stmt FROM @frontend_client_sql;
EXECUTE frontend_client_stmt;
DEALLOCATE PREPARE frontend_client_stmt;

UPDATE email_verification
SET frontend_client = 'primary'
WHERE frontend_client IS NULL OR frontend_client NOT IN ('primary', 'sites');
