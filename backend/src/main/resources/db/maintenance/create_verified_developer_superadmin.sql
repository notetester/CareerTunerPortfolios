-- A~F 전용 또는 명시적으로 승인한 개발자 SUPER_ADMIN 계정을 생성한다.
-- plaintext 비밀번호를 입력하거나 출력하지 않으며 cost 10~14 BCrypt hash만 받는다.
-- 동일 스크립트로 생성된 완전 동일 계정의 재실행만 no-op이고 기존 계정 충돌은 fail-closed 한다.

START TRANSACTION;

SET @ct_target_email := LOWER(NULLIF(TRIM(COALESCE(@ct_target_email, '')), ''));
SET @ct_target_name := NULLIF(TRIM(COALESCE(@ct_target_name, '')), '');
SET @ct_password_hash := NULLIF(TRIM(COALESCE(@ct_password_hash, '')), '');
SET @ct_operator_email := LOWER(NULLIF(TRIM(COALESCE(@ct_operator_email, '')), ''));
SET @ct_change_ref := NULLIF(TRIM(COALESCE(@ct_change_ref, '')), '');
SET @ct_reason := NULLIF(TRIM(COALESCE(@ct_reason, '')), '');
SET @ct_allow_external_email := IF(COALESCE(@ct_allow_external_email, 0) = 1, 1, 0);
SET @ct_public_seed_password_hash := '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja';
SET @ct_quorum_lock_name := CONCAT(DATABASE(), ':careertuner-superadmin-quorum');
SET @ct_quorum_lock_acquired := GET_LOCK(@ct_quorum_lock_name, 10);

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

SET @ct_existing_id := NULL;
SET @ct_existing_name := NULL;
SET @ct_existing_password_hash := NULL;
SET @ct_existing_role := NULL;
SET @ct_existing_status := NULL;
SET @ct_existing_email_verified := NULL;
SELECT existing.id,
       existing.name,
       existing.password,
       existing.role,
       existing.status,
       existing.email_verified
  INTO @ct_existing_id,
       @ct_existing_name,
       @ct_existing_password_hash,
       @ct_existing_role,
       @ct_existing_status,
       @ct_existing_email_verified
  FROM users existing
 WHERE LOWER(existing.email) = @ct_target_email
 FOR UPDATE;

SET @ct_existing_created_by_ops := IF(
    @ct_existing_id IS NULL,
    0,
    (
        SELECT IF(COUNT(*) > 0, 1, 0)
          FROM admin_action_log action_log
         WHERE action_log.target_user_id = @ct_existing_id
           AND action_log.action_type = 'SUPER_ADMIN_ACCOUNT_CREATED_BY_DB_OPS'
    )
);
SET @ct_hash_owner_count := (
    SELECT COUNT(*)
      FROM users hash_owner
     WHERE hash_owner.password = @ct_password_hash
       AND (@ct_existing_id IS NULL OR hash_owner.id <> @ct_existing_id)
);
SET @ct_is_dedicated_email := IF(
    @ct_target_email REGEXP '^dev-admin-[a-f]@careertuner\\.dev$',
    1, 0
);
SET @ct_is_valid_bcrypt_hash := IF(
    @ct_password_hash REGEXP '^\\$2[aby]\\$1[0-4]\\$[./A-Za-z0-9]{53}$',
    1, 0
);
SET @ct_existing_is_idempotent_match := IF(
       @ct_existing_id IS NOT NULL
   AND @ct_existing_name = @ct_target_name
   AND @ct_existing_password_hash = @ct_password_hash
   AND @ct_existing_role = 'SUPER_ADMIN'
   AND @ct_existing_status = 'ACTIVE'
   AND @ct_existing_email_verified = 1
   AND @ct_existing_created_by_ops = 1,
    1, 0
);

DROP TEMPORARY TABLE IF EXISTS ct_superadmin_create_guard;
CREATE TEMPORARY TABLE ct_superadmin_create_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);

INSERT INTO ct_superadmin_create_guard (guard_ok)
SELECT IF(
       @ct_quorum_lock_acquired = 1
   AND @ct_operator_id IS NOT NULL
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
   AND (@ct_is_dedicated_email = 1 OR @ct_allow_external_email = 1)
   AND @ct_target_email NOT IN (
       'admin@careertuner.dev',
       'jiwon.kim@careertuner.dev',
       'seoyeon.lee@careertuner.dev',
       'minsu.park@careertuner.dev',
       'pending@careertuner.dev'
   )
   AND CHAR_LENGTH(@ct_target_name) BETWEEN 2 AND 100
   AND @ct_is_valid_bcrypt_hash = 1
   AND @ct_password_hash <> @ct_public_seed_password_hash
   AND @ct_hash_owner_count = 0
   AND CHAR_LENGTH(@ct_change_ref) >= 5
   AND CHAR_LENGTH(@ct_reason) >= 10
   AND (@ct_existing_id IS NULL OR @ct_existing_is_idempotent_match = 1),
       1, 0
);

SET @ct_created := IF(@ct_existing_id IS NULL, 1, 0);

INSERT INTO users (
    email, password, password_enabled, name, email_verified,
    user_type, role, status, plan, credit
)
SELECT @ct_target_email,
       @ct_password_hash,
       1,
       @ct_target_name,
       1,
       'JOB_SEEKER',
       'SUPER_ADMIN',
       'ACTIVE',
       'FREE',
       0
  FROM ct_superadmin_create_guard guard
 WHERE guard.guard_ok = 1
   AND @ct_existing_id IS NULL;

SET @ct_target_id := IF(@ct_created = 1, LAST_INSERT_ID(), @ct_existing_id);

INSERT INTO admin_permission_audit (
    actor_user_id, target_user_id, action_type, permission_code, group_code, reason
)
SELECT @ct_operator_id,
       @ct_target_id,
       'SUPER_ADMIN_ACCOUNT_CREATED',
       NULL,
       NULL,
       LEFT(CONCAT('[', @ct_change_ref, '] ', @ct_reason), 500)
  FROM ct_superadmin_create_guard guard
 WHERE guard.guard_ok = 1
   AND @ct_created = 1;

INSERT INTO admin_action_log (
    actor_user_id, target_user_id, action_type, target_type,
    before_value, after_value, reason, ip_address, user_agent
)
SELECT @ct_operator_id,
       @ct_target_id,
       'SUPER_ADMIN_ACCOUNT_CREATED_BY_DB_OPS',
       'ADMIN_USER',
       NULL,
       JSON_OBJECT(
           'email', @ct_target_email,
           'role', 'SUPER_ADMIN',
           'status', 'ACTIVE',
           'emailVerified', TRUE,
           'passwordAlgorithm', 'BCRYPT'
       ),
       LEFT(CONCAT('[', @ct_change_ref, '] ', @ct_reason), 500),
       NULL,
       'db-maintenance/create_verified_developer_superadmin'
  FROM ct_superadmin_create_guard guard
 WHERE guard.guard_ok = 1
   AND @ct_created = 1;

COMMIT;

-- hash와 plaintext는 출력하지 않는다.
SELECT target.id,
       target.email,
       target.name,
       target.role,
       target.status,
       target.email_verified,
       @ct_created AS created,
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
DROP TEMPORARY TABLE IF EXISTS ct_superadmin_create_guard;
