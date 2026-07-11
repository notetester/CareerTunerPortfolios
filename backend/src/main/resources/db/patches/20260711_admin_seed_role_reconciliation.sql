-- 공유 team/AWS DB에 남은 고정 seed의 관리자 역할 정합 복구.
--
-- 주의:
-- - 알려진 공통 비밀번호를 쓰는 seed는 공개 AWS가 연결된 공유 DB에서 관리자 역할이면 안 된다.
-- - id와 email이 모두 예상값과 일치하지 않으면 CHECK guard가 실패하며 어떤 역할도 바꾸지 않는다.
-- - seed 외 SUPER_ADMIN은 역할·권한·토큰을 수정하지 않고 현재 권한 현황 baseline만 1회 관측 기록한다.
-- - seed/공용 비밀번호를 제외한 검증된 ACTIVE SUPER_ADMIN이 최소 3명 있어야만 실행된다.
--   테스트·운영 관리자 quorum을 줄이지 않고 알려진 seed만 정리한다.
-- - 역할/배정이 이미 목표 상태이고 baseline 감사가 있으면 재실행해도 로그·토큰을 다시 건드리지 않는다.

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS ct_seed_role_expected;
CREATE TEMPORARY TABLE ct_seed_role_expected (
    expected_id BIGINT       NOT NULL PRIMARY KEY,
    seed_key    VARCHAR(30)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    target_role VARCHAR(20)  NOT NULL
);

INSERT INTO ct_seed_role_expected (expected_id, seed_key, email, target_role)
VALUES
    (1, 'admin',   'admin@careertuner.dev',       'USER'),
    (2, 'jiwon',   'jiwon.kim@careertuner.dev',   'USER'),
    (3, 'seoyeon', 'seoyeon.lee@careertuner.dev', 'USER'),
    (4, 'minsu',   'minsu.park@careertuner.dev',  'USER'),
    (5, 'pending', 'pending@careertuner.dev',     'USER');

-- 잘못된 DB나 다른 환경에 seed 보정이 적용되지 않도록 fail-closed 한다.
-- 지정 admin은 활성 상태여야 하며, 대상 seed의 활성 권한 배정 중복도 없어야 한다.
DROP TEMPORARY TABLE IF EXISTS ct_seed_role_guard;
CREATE TEMPORARY TABLE ct_seed_role_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);

-- MySQL은 한 statement 안에서 같은 TEMPORARY TABLE을 여러 번 열 수 없으므로
-- 각 조건을 별도 statement로 계산한 뒤 최종 guard에는 스칼라 변수만 사용한다.
SET @ct_seed_identity_match_count := (
    SELECT COUNT(*)
      FROM ct_seed_role_expected expected
      JOIN users seed_user
        ON seed_user.id = expected.expected_id
       AND seed_user.email = expected.email
);
SET @ct_seed_admin_active_count := (
    SELECT COUNT(*)
      FROM users seed_admin
     WHERE seed_admin.id = 1
       AND seed_admin.email = 'admin@careertuner.dev'
       AND seed_admin.status = 'ACTIVE'
);
SET @ct_nonseed_active_superadmin_count := (
    SELECT COUNT(*)
      FROM users privileged
 LEFT JOIN ct_seed_role_expected seed
        ON seed.expected_id = privileged.id
       AND seed.email = privileged.email
     WHERE privileged.role = 'SUPER_ADMIN'
       AND privileged.status = 'ACTIVE'
       AND privileged.email_verified = 1
       AND privileged.password_enabled = 1
       AND privileged.password IS NOT NULL
       AND CHAR_LENGTH(privileged.password) = 60
       AND privileged.password REGEXP '^\\$2[aby]\\$1[0-4]\\$[./A-Za-z0-9]{53}$'
       AND privileged.password <> '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja'
       AND seed.expected_id IS NULL
);
SET @ct_seed_permission_duplicate_count := (
    SELECT COUNT(*)
      FROM (
          SELECT assignment.user_id, assignment.permission_code
            FROM admin_user_permission assignment
            JOIN ct_seed_role_expected seed
              ON seed.expected_id = assignment.user_id
           WHERE assignment.revoked_at IS NULL
           GROUP BY assignment.user_id, assignment.permission_code
          HAVING COUNT(*) > 1
      ) duplicates
);
SET @ct_seed_group_duplicate_count := (
    SELECT COUNT(*)
      FROM (
          SELECT assignment.user_id, assignment.group_code
            FROM admin_user_group assignment
            JOIN ct_seed_role_expected seed
              ON seed.expected_id = assignment.user_id
           WHERE assignment.revoked_at IS NULL
           GROUP BY assignment.user_id, assignment.group_code
          HAVING COUNT(*) > 1
      ) duplicates
);

INSERT INTO ct_seed_role_guard (guard_ok)
SELECT CASE
           WHEN @ct_seed_identity_match_count = 5
            AND @ct_seed_admin_active_count = 1
            AND @ct_nonseed_active_superadmin_count >= 3
            AND @ct_seed_permission_duplicate_count = 0
            AND @ct_seed_group_duplicate_count = 0
           THEN 1 ELSE 0
       END;

DROP TEMPORARY TABLE IF EXISTS ct_seed_role_state;
CREATE TEMPORARY TABLE ct_seed_role_state (
    user_id                 BIGINT      NOT NULL PRIMARY KEY,
    previous_role           VARCHAR(20) NOT NULL,
    target_role             VARCHAR(20) NOT NULL,
    role_changed            TINYINT     NOT NULL,
    active_permission_count INT         NOT NULL,
    active_group_count      INT         NOT NULL,
    baseline_needed         TINYINT     NOT NULL
);

INSERT INTO ct_seed_role_state (
    user_id, previous_role, target_role, role_changed,
    active_permission_count, active_group_count, baseline_needed
)
SELECT u.id,
       u.role,
       e.target_role,
       IF(u.role <> e.target_role, 1, 0),
       (SELECT COUNT(*)
          FROM admin_user_permission aup
         WHERE aup.user_id = u.id
           AND aup.revoked_at IS NULL),
       (SELECT COUNT(*)
          FROM admin_user_group aug
         WHERE aug.user_id = u.id
           AND aug.revoked_at IS NULL),
       IF(EXISTS (
              SELECT 1
                FROM admin_action_log aal
               WHERE aal.target_user_id = u.id
                 AND aal.action_type = 'SEED_ROLE_BASELINE_20260711'
          ), 0, 1)
  FROM ct_seed_role_expected e
  JOIN users u
    ON u.id = e.expected_id
   AND u.email = e.email
  JOIN ct_seed_role_guard guard ON guard.guard_ok = 1;

-- 실제 role 변경은 정본 seed 5개에 한정한다.
UPDATE users u
JOIN ct_seed_role_expected e
  ON e.expected_id = u.id
 AND e.email = u.email
JOIN ct_seed_role_guard guard ON guard.guard_ok = 1
   SET u.role = e.target_role,
       u.updated_at = NOW()
 WHERE u.role <> e.target_role;

-- seed 관리자/일반 사용자에 남은 직접 권한·그룹은 삭제하지 않고 회수 이력으로 보존한다.
UPDATE admin_user_permission aup
JOIN ct_seed_role_expected e ON e.expected_id = aup.user_id
JOIN ct_seed_role_guard guard ON guard.guard_ok = 1
   SET aup.revoked_at = NOW()
 WHERE aup.revoked_at IS NULL;

UPDATE admin_user_group aug
JOIN ct_seed_role_expected e ON e.expected_id = aug.user_id
JOIN ct_seed_role_guard guard ON guard.guard_ok = 1
   SET aug.revoked_at = NOW()
 WHERE aug.revoked_at IS NULL;

-- 최초 baseline 또는 실제 role/배정 변경 대상만 재로그인하도록 refresh token을 회수한다.
UPDATE refresh_token rt
JOIN ct_seed_role_state s ON s.user_id = rt.user_id
   SET rt.revoked = 1,
       rt.revoked_at = COALESCE(rt.revoked_at, NOW())
 WHERE rt.revoked = 0
   AND (s.baseline_needed = 1
        OR s.role_changed = 1
        OR s.active_permission_count > 0
        OR s.active_group_count > 0);

INSERT INTO user_role_change_history (
    user_id, previous_role, new_role, reason, changed_by
)
SELECT s.user_id,
       s.previous_role,
       s.target_role,
       '20260711 공유 DB seed 역할 정합 복구',
       NULL
  FROM ct_seed_role_state s
 WHERE s.role_changed = 1;

INSERT INTO admin_permission_audit (
    actor_user_id, target_user_id, action_type, permission_code, group_code, reason
)
SELECT NULL,
       s.user_id,
       'SEED_ROLE_RECONCILED',
       NULL,
       NULL,
       CONCAT('role ', s.previous_role, ' -> ', s.target_role,
              ', direct grants ', s.active_permission_count,
              ', group grants ', s.active_group_count)
  FROM ct_seed_role_state s
 WHERE s.role_changed = 1
    OR s.active_permission_count > 0
    OR s.active_group_count > 0;

-- baseline marker는 seed별 최초 1회만 기록한다.
INSERT INTO admin_action_log (
    actor_user_id, target_user_id, action_type, target_type,
    before_value, after_value, reason, ip_address, user_agent
)
SELECT NULL,
       s.user_id,
       'SEED_ROLE_BASELINE_20260711',
       'ADMIN_USER',
       JSON_OBJECT(
           'role', s.previous_role,
           'activeDirectPermissions', s.active_permission_count,
           'activeGroups', s.active_group_count
       ),
       JSON_OBJECT(
           'role', s.target_role,
           'activeDirectPermissions', 0,
           'activeGroups', 0,
           'refreshTokensRevoked', TRUE
       ),
       '공유 DB seed 역할 정합 복구. 운영 bootstrap에는 사용하지 않음.',
       NULL,
       'db-patch/20260711_admin_seed_role_reconciliation'
  FROM ct_seed_role_state s
 WHERE s.baseline_needed = 1
   AND NOT EXISTS (
       SELECT 1
         FROM admin_action_log existing
        WHERE existing.target_user_id = s.user_id
          AND existing.action_type = 'SEED_ROLE_BASELINE_20260711'
   );

-- 실제 역할/배정 변경은 별도 이벤트로 기록한다. 무변경 재실행 때는 행이 생성되지 않는다.
INSERT INTO admin_action_log (
    actor_user_id, target_user_id, action_type, target_type,
    before_value, after_value, reason, ip_address, user_agent
)
SELECT NULL,
       s.user_id,
       'SEED_ROLE_RECONCILED_20260711',
       'ADMIN_USER',
       JSON_OBJECT(
           'role', s.previous_role,
           'activeDirectPermissions', s.active_permission_count,
           'activeGroups', s.active_group_count
       ),
       JSON_OBJECT(
           'role', s.target_role,
           'activeDirectPermissions', 0,
           'activeGroups', 0,
           'refreshTokensRevoked', TRUE
       ),
       '공유 DB seed 역할 또는 권한 배정 정합 복구',
       NULL,
       'db-patch/20260711_admin_seed_role_reconciliation'
  FROM ct_seed_role_state s
 WHERE s.role_changed = 1
    OR s.active_permission_count > 0
    OR s.active_group_count > 0;

-- seed 외 ACTIVE SUPER_ADMIN은 실사용 여부 확인 전 절대 변경하지 않는다.
-- 20260711 시점의 role/활성 배정 수만 1회 기록하며 승격의 정당성을 의미하지 않는다.
INSERT INTO admin_action_log (
    actor_user_id, target_user_id, action_type, target_type,
    before_value, after_value, reason, ip_address, user_agent
)
SELECT NULL,
       privileged.id,
       'NONSEED_SUPERADMIN_BASELINE_20260711',
       'ADMIN_USER',
       NULL,
       JSON_OBJECT(
           'role', privileged.role,
           'status', privileged.status,
           'activeDirectPermissions', (
               SELECT COUNT(*)
                 FROM admin_user_permission aup
                WHERE aup.user_id = privileged.id
                  AND aup.revoked_at IS NULL
           ),
           'activeGroups', (
               SELECT COUNT(*)
                 FROM admin_user_group aug
                WHERE aug.user_id = privileged.id
                  AND aug.revoked_at IS NULL
           )
       ),
       '20260711 권한 현황 관측 baseline. 승격 정당화 아님, 소유자 확인 전 변경 금지.',
       NULL,
       'db-patch/20260711_admin_seed_role_reconciliation'
  FROM users privileged
  JOIN ct_seed_role_guard guard ON guard.guard_ok = 1
LEFT JOIN ct_seed_role_expected seed
       ON seed.expected_id = privileged.id
      AND seed.email = privileged.email
 WHERE privileged.role = 'SUPER_ADMIN'
   AND privileged.status = 'ACTIVE'
   AND seed.expected_id IS NULL
   AND NOT EXISTS (
       SELECT 1
         FROM admin_action_log existing
        WHERE existing.target_user_id = privileged.id
          AND existing.action_type = 'NONSEED_SUPERADMIN_BASELINE_20260711'
   );

COMMIT;

-- 적용 후 검증: 5행 모두 USER이고 활성 배정은 모두 0이어야 한다.
SELECT e.seed_key,
       u.id,
       u.email,
       u.role,
       u.status,
       (SELECT COUNT(*)
          FROM admin_user_permission aup
         WHERE aup.user_id = u.id AND aup.revoked_at IS NULL) AS active_permission_count,
       (SELECT COUNT(*)
          FROM admin_user_group aug
         WHERE aug.user_id = u.id AND aug.revoked_at IS NULL) AS active_group_count,
       (SELECT COUNT(*)
          FROM refresh_token rt
         WHERE rt.user_id = u.id AND rt.revoked = 0) AS active_refresh_token_count
  FROM ct_seed_role_expected e
  JOIN users u
    ON u.id = e.expected_id
   AND u.email = e.email
 ORDER BY e.expected_id;

DROP TEMPORARY TABLE IF EXISTS ct_seed_role_state;
DROP TEMPORARY TABLE IF EXISTS ct_seed_role_guard;
DROP TEMPORARY TABLE IF EXISTS ct_seed_role_expected;
