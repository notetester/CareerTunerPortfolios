-- 알려진 seed/공용 비밀번호를 제외한 안전한 ACTIVE SUPER_ADMIN이 3명 이상인지 검증한다.
-- 조건을 만족하지 않으면 CHECK guard가 실패한다. 영구 데이터는 수정하지 않는다.

SET @ct_public_seed_password_hash := '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja';

DROP TEMPORARY TABLE IF EXISTS ct_safe_active_superadmin;
CREATE TEMPORARY TABLE ct_safe_active_superadmin AS
SELECT privileged.id,
       privileged.email,
       privileged.name,
       privileged.status,
       privileged.email_verified,
       privileged.last_login_at
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
   AND privileged.password <> @ct_public_seed_password_hash;

DROP TEMPORARY TABLE IF EXISTS ct_superadmin_quorum_guard;
CREATE TEMPORARY TABLE ct_superadmin_quorum_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);

SET @ct_safe_active_superadmin_count := (
    SELECT COUNT(*) FROM ct_safe_active_superadmin
);

INSERT INTO ct_superadmin_quorum_guard (guard_ok)
VALUES (IF(@ct_safe_active_superadmin_count >= 3, 1, 0));

SELECT @ct_safe_active_superadmin_count AS safe_active_superadmin_count,
       3 AS minimum_required,
       IF(@ct_safe_active_superadmin_count >= 3, 'PASS', 'FAIL') AS result;

SELECT id, email, name, status, email_verified, last_login_at
  FROM ct_safe_active_superadmin
 ORDER BY id;

DROP TEMPORARY TABLE IF EXISTS ct_superadmin_quorum_guard;
DROP TEMPORARY TABLE IF EXISTS ct_safe_active_superadmin;
