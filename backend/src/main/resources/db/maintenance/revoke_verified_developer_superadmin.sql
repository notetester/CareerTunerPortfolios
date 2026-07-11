-- 비seed 개발자의 SUPER_ADMIN과 남은 관리자 배정을 모두 회수해 USER로 복구한다.
-- 안전한 ACTIVE SUPER_ADMIN이 3명 미만으로 내려가면 fail-closed 한다.

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
SELECT target.id, target.role, target.status
  INTO @ct_target_id, @ct_target_role, @ct_target_status
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

SET @ct_safe_active_superadmin_count := (
    SELECT COUNT(*)
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
);
SET @ct_target_counts_in_safe_quorum := (
    SELECT IF(COUNT(*) = 1, 1, 0)
      FROM users target
     WHERE target.id = @ct_target_id
       AND target.role = 'SUPER_ADMIN'
       AND target.status = 'ACTIVE'
       AND target.email_verified = 1
       AND target.password_enabled = 1
       AND target.password IS NOT NULL
       AND CHAR_LENGTH(target.password) = 60
       AND target.password REGEXP '^\\$2[aby]\\$1[0-4]\\$[./A-Za-z0-9]{53}$'
       AND target.email NOT IN (
           'admin@careertuner.dev',
           'jiwon.kim@careertuner.dev',
           'seoyeon.lee@careertuner.dev',
           'minsu.park@careertuner.dev',
           'pending@careertuner.dev'
       )
       AND target.password <> @ct_public_seed_password_hash
);
SET @ct_active_permission_count := (
    SELECT COUNT(*)
      FROM admin_user_permission assignment
     WHERE assignment.user_id = @ct_target_id
       AND assignment.revoked_at IS NULL
);
SET @ct_active_group_count := (
    SELECT COUNT(*)
      FROM admin_user_group assignment
     WHERE assignment.user_id = @ct_target_id
       AND assignment.revoked_at IS NULL
);
SET @ct_active_refresh_count := (
    SELECT COUNT(*)
      FROM refresh_token token
     WHERE token.user_id = @ct_target_id
       AND token.revoked = 0
);

DROP TEMPORARY TABLE IF EXISTS ct_superadmin_revoke_guard;
CREATE TEMPORARY TABLE ct_superadmin_revoke_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);

INSERT INTO ct_superadmin_revoke_guard (guard_ok)
SELECT IF(
       @ct_quorum_lock_acquired = 1
   AND @ct_target_id IS NOT NULL
   AND @ct_operator_id IS NOT NULL
   AND @ct_target_id <> @ct_operator_id
   AND @ct_target_role IN ('USER', 'SUPER_ADMIN')
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
   AND @ct_safe_active_superadmin_count - @ct_target_counts_in_safe_quorum >= 3
   AND CHAR_LENGTH(@ct_change_ref) >= 5
   AND CHAR_LENGTH(@ct_reason) >= 10,
       1, 0
);

SET @ct_role_changed := IF(@ct_target_role = 'SUPER_ADMIN', 1, 0);
SET @ct_any_change := IF(
    @ct_role_changed = 1
    OR @ct_active_permission_count > 0
    OR @ct_active_group_count > 0
    OR @ct_active_refresh_count > 0,
    1, 0
);

UPDATE users target
JOIN ct_superadmin_revoke_guard guard ON guard.guard_ok = 1
   SET target.role = 'USER',
       target.updated_at = NOW()
 WHERE target.id = @ct_target_id
   AND target.role = 'SUPER_ADMIN';

UPDATE admin_user_permission assignment
JOIN ct_superadmin_revoke_guard guard ON guard.guard_ok = 1
   SET assignment.revoked_at = NOW()
 WHERE assignment.user_id = @ct_target_id
   AND assignment.revoked_at IS NULL;

UPDATE admin_user_group assignment
JOIN ct_superadmin_revoke_guard guard ON guard.guard_ok = 1
   SET assignment.revoked_at = NOW()
 WHERE assignment.user_id = @ct_target_id
   AND assignment.revoked_at IS NULL;

UPDATE refresh_token token
JOIN ct_superadmin_revoke_guard guard ON guard.guard_ok = 1
   SET token.revoked = 1,
       token.revoked_at = COALESCE(token.revoked_at, NOW())
 WHERE token.user_id = @ct_target_id
   AND token.revoked = 0;

INSERT INTO user_role_change_history (
    user_id, previous_role, new_role, reason, changed_by
)
SELECT @ct_target_id,
       @ct_target_role,
       'USER',
       LEFT(CONCAT('[', @ct_change_ref, '] ', @ct_reason), 500),
       @ct_operator_id
  FROM ct_superadmin_revoke_guard guard
 WHERE guard.guard_ok = 1
   AND @ct_role_changed = 1;

INSERT INTO admin_permission_audit (
    actor_user_id, target_user_id, action_type, permission_code, group_code, reason
)
SELECT @ct_operator_id,
       @ct_target_id,
       'SUPER_ADMIN_REVOKED',
       NULL,
       NULL,
       LEFT(CONCAT('[', @ct_change_ref, '] ', @ct_reason), 500)
  FROM ct_superadmin_revoke_guard guard
 WHERE guard.guard_ok = 1
   AND @ct_any_change = 1;

INSERT INTO admin_action_log (
    actor_user_id, target_user_id, action_type, target_type,
    before_value, after_value, reason, ip_address, user_agent
)
SELECT @ct_operator_id,
       @ct_target_id,
       'SUPER_ADMIN_REVOKED_BY_DB_OPS',
       'ADMIN_USER',
       JSON_OBJECT(
           'role', @ct_target_role,
           'activeDirectPermissions', @ct_active_permission_count,
           'activeGroups', @ct_active_group_count,
           'activeRefreshTokens', @ct_active_refresh_count
       ),
       JSON_OBJECT(
           'role', 'USER',
           'activeDirectPermissions', 0,
           'activeGroups', 0,
           'activeRefreshTokens', 0
       ),
       LEFT(CONCAT('[', @ct_change_ref, '] ', @ct_reason), 500),
       NULL,
       'db-maintenance/revoke_verified_developer_superadmin'
  FROM ct_superadmin_revoke_guard guard
 WHERE guard.guard_ok = 1
   AND @ct_any_change = 1;

COMMIT;

SELECT target.id,
       target.email,
       target.role,
       target.status,
       @ct_any_change AS changed,
       @ct_safe_active_superadmin_count - @ct_target_counts_in_safe_quorum AS safe_active_superadmin_count
  FROM users target
 WHERE target.id = @ct_target_id;

DO RELEASE_LOCK(@ct_quorum_lock_name);
DROP TEMPORARY TABLE IF EXISTS ct_superadmin_revoke_guard;
