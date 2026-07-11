-- 이메일 인증을 마친 기존 개발자 계정을 SUPER_ADMIN으로 승격한다.
-- 반드시 README의 one-shot mysql 세션 절차로 실행한다. 입력 변수가 없으면 fail-closed 한다.

START TRANSACTION;

SET @ct_target_email := LOWER(NULLIF(TRIM(COALESCE(@ct_target_email, '')), ''));
SET @ct_operator_email := LOWER(NULLIF(TRIM(COALESCE(@ct_operator_email, '')), ''));
SET @ct_change_ref := NULLIF(TRIM(COALESCE(@ct_change_ref, '')), '');
SET @ct_reason := NULLIF(TRIM(COALESCE(@ct_reason, '')), '');
SET @ct_public_seed_password_hash := '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja';
SET @ct_quorum_lock_name := CONCAT(DATABASE(), ':careertuner-superadmin-quorum');
SET @ct_quorum_lock_acquired := GET_LOCK(@ct_quorum_lock_name, 10);

SET @ct_target_id := NULL;
SET @ct_target_role := NULL;
SET @ct_target_status := NULL;
SET @ct_target_email_verified := NULL;
SET @ct_target_uses_public_password := NULL;
SELECT target.id,
       target.role,
       target.status,
       target.email_verified,
       IF(target.password_enabled <> 1
          OR target.password IS NULL
          OR CHAR_LENGTH(target.password) <> 60
          OR target.password NOT REGEXP '^\\$2[aby]\\$1[0-4]\\$[./A-Za-z0-9]{53}$'
          OR target.password = @ct_public_seed_password_hash, 1, 0)
  INTO @ct_target_id,
       @ct_target_role,
       @ct_target_status,
       @ct_target_email_verified,
       @ct_target_uses_public_password
  FROM users target
 WHERE LOWER(target.email) = @ct_target_email
 FOR UPDATE;

SET @ct_operator_id := NULL;
SET @ct_operator_role := NULL;
SET @ct_operator_status := NULL;
SET @ct_operator_email_verified := NULL;
SET @ct_operator_uses_public_password := NULL;
SELECT operator.id,
       operator.role,
       operator.status,
       operator.email_verified,
       IF(operator.password_enabled <> 1
          OR operator.password IS NULL
          OR CHAR_LENGTH(operator.password) <> 60
          OR operator.password NOT REGEXP '^\\$2[aby]\\$1[0-4]\\$[./A-Za-z0-9]{53}$'
          OR operator.password = @ct_public_seed_password_hash, 1, 0)
  INTO @ct_operator_id,
       @ct_operator_role,
       @ct_operator_status,
       @ct_operator_email_verified,
       @ct_operator_uses_public_password
  FROM users operator
 WHERE LOWER(operator.email) = @ct_operator_email
 FOR UPDATE;

DROP TEMPORARY TABLE IF EXISTS ct_superadmin_grant_guard;
CREATE TEMPORARY TABLE ct_superadmin_grant_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);

INSERT INTO ct_superadmin_grant_guard (guard_ok)
SELECT IF(
       @ct_quorum_lock_acquired = 1
   AND @ct_target_id IS NOT NULL
   AND @ct_operator_id IS NOT NULL
   AND @ct_target_id <> @ct_operator_id
   AND @ct_target_status = 'ACTIVE'
   AND @ct_target_email_verified = 1
   AND @ct_target_role IN ('USER', 'ADMIN', 'SUPER_ADMIN')
   AND @ct_target_uses_public_password = 0
   AND @ct_target_email NOT IN (
       'admin@careertuner.dev',
       'jiwon.kim@careertuner.dev',
       'seoyeon.lee@careertuner.dev',
       'minsu.park@careertuner.dev',
       'pending@careertuner.dev'
   )
   AND @ct_operator_role = 'SUPER_ADMIN'
   AND @ct_operator_status = 'ACTIVE'
   AND @ct_operator_email_verified = 1
   AND @ct_operator_uses_public_password = 0
   AND @ct_operator_email NOT IN (
       'admin@careertuner.dev',
       'jiwon.kim@careertuner.dev',
       'seoyeon.lee@careertuner.dev',
       'minsu.park@careertuner.dev',
       'pending@careertuner.dev'
   )
   AND CHAR_LENGTH(@ct_change_ref) >= 5
   AND CHAR_LENGTH(@ct_reason) >= 10,
       1, 0
);

SET @ct_role_changed := IF(@ct_target_role <> 'SUPER_ADMIN', 1, 0);

UPDATE users target
JOIN ct_superadmin_grant_guard guard ON guard.guard_ok = 1
   SET target.role = 'SUPER_ADMIN',
       target.updated_at = NOW()
 WHERE target.id = @ct_target_id
   AND target.role <> 'SUPER_ADMIN';

UPDATE refresh_token token
JOIN ct_superadmin_grant_guard guard ON guard.guard_ok = 1
   SET token.revoked = 1,
       token.revoked_at = COALESCE(token.revoked_at, NOW())
 WHERE token.user_id = @ct_target_id
   AND token.revoked = 0
   AND @ct_role_changed = 1;

INSERT INTO user_role_change_history (
    user_id, previous_role, new_role, reason, changed_by
)
SELECT @ct_target_id,
       @ct_target_role,
       'SUPER_ADMIN',
       LEFT(CONCAT('[', @ct_change_ref, '] ', @ct_reason), 500),
       @ct_operator_id
  FROM ct_superadmin_grant_guard guard
 WHERE guard.guard_ok = 1
   AND @ct_role_changed = 1;

INSERT INTO admin_permission_audit (
    actor_user_id, target_user_id, action_type, permission_code, group_code, reason
)
SELECT @ct_operator_id,
       @ct_target_id,
       'SUPER_ADMIN_GRANTED',
       NULL,
       NULL,
       LEFT(CONCAT('[', @ct_change_ref, '] ', @ct_reason), 500)
  FROM ct_superadmin_grant_guard guard
 WHERE guard.guard_ok = 1
   AND @ct_role_changed = 1;

INSERT INTO admin_action_log (
    actor_user_id, target_user_id, action_type, target_type,
    before_value, after_value, reason, ip_address, user_agent
)
SELECT @ct_operator_id,
       @ct_target_id,
       'SUPER_ADMIN_GRANTED_BY_DB_OPS',
       'ADMIN_USER',
       JSON_OBJECT('role', @ct_target_role),
       JSON_OBJECT('role', 'SUPER_ADMIN', 'refreshTokensRevoked', TRUE),
       LEFT(CONCAT('[', @ct_change_ref, '] ', @ct_reason), 500),
       NULL,
       'db-maintenance/grant_verified_developer_superadmin'
  FROM ct_superadmin_grant_guard guard
 WHERE guard.guard_ok = 1
   AND @ct_role_changed = 1;

COMMIT;

SELECT target.id,
       target.email,
       target.role,
       target.status,
       target.email_verified,
       @ct_role_changed AS changed,
       (SELECT COUNT(*)
          FROM users privileged
         WHERE privileged.role = 'SUPER_ADMIN'
           AND privileged.status = 'ACTIVE'
           AND privileged.email_verified = 1
           AND privileged.password_enabled = 1
           AND privileged.password IS NOT NULL
           AND CHAR_LENGTH(privileged.password) = 60
           AND privileged.password REGEXP '^\\$2[aby]\\$1[0-4]\\$[./A-Za-z0-9]{53}$'
           AND privileged.email NOT IN (
               'admin@careertuner.dev',
               'jiwon.kim@careertuner.dev',
               'seoyeon.lee@careertuner.dev',
               'minsu.park@careertuner.dev',
               'pending@careertuner.dev'
           )
           AND privileged.password <> @ct_public_seed_password_hash
       ) AS safe_active_superadmin_count
  FROM users target
 WHERE target.id = @ct_target_id;

DO RELEASE_LOCK(@ct_quorum_lock_name);
DROP TEMPORARY TABLE IF EXISTS ct_superadmin_grant_guard;
