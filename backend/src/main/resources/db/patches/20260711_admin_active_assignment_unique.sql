-- 관리자 직접 권한/그룹의 활성 배정을 사용자·코드별 1건으로 강제한다.
--
-- 기존 UNIQUE(user_id, code, revoked_at)는 MySQL에서 revoked_at=NULL 중복을 막지 못한다.
-- 이 패치는 중복이 있으면 가장 큰 id 1건만 활성으로 남기고 나머지는 회수한 뒤,
-- generated active key 기반 UNIQUE 인덱스로 교체한다. 재실행 시 무변경 no-op이다.
-- DDL은 implicit commit이므로 애플리케이션 쓰기를 멈춘 유지보수 구간에 적용한다.

DROP TEMPORARY TABLE IF EXISTS ct_duplicate_active_permissions;
CREATE TEMPORARY TABLE ct_duplicate_active_permissions AS
SELECT aup.id, aup.user_id, aup.permission_code
  FROM admin_user_permission aup
  JOIN (
      SELECT user_id, permission_code, MAX(id) AS keep_id
        FROM admin_user_permission
       WHERE revoked_at IS NULL
       GROUP BY user_id, permission_code
      HAVING COUNT(*) > 1
  ) duplicated
    ON duplicated.user_id = aup.user_id
   AND duplicated.permission_code = aup.permission_code
   AND duplicated.keep_id <> aup.id
 WHERE aup.revoked_at IS NULL;

DROP TEMPORARY TABLE IF EXISTS ct_duplicate_active_groups;
CREATE TEMPORARY TABLE ct_duplicate_active_groups AS
SELECT aug.id, aug.user_id, aug.group_code
  FROM admin_user_group aug
  JOIN (
      SELECT user_id, group_code, MAX(id) AS keep_id
        FROM admin_user_group
       WHERE revoked_at IS NULL
       GROUP BY user_id, group_code
      HAVING COUNT(*) > 1
  ) duplicated
    ON duplicated.user_id = aug.user_id
   AND duplicated.group_code = aug.group_code
   AND duplicated.keep_id <> aug.id
 WHERE aug.revoked_at IS NULL;

-- generated column을 먼저 보강한다.
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_user_permission'
       AND COLUMN_NAME = 'active_assignment_key'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE admin_user_permission ADD COLUMN active_assignment_key TINYINT GENERATED ALWAYS AS (CASE WHEN revoked_at IS NULL THEN 1 ELSE NULL END) STORED COMMENT ''활성 배정 1건 유니크 보장용'' AFTER revoked_at',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_user_group'
       AND COLUMN_NAME = 'active_assignment_key'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE admin_user_group ADD COLUMN active_assignment_key TINYINT GENERATED ALWAYS AS (CASE WHEN revoked_at IS NULL THEN 1 ELSE NULL END) STORED COMMENT ''활성 배정 1건 유니크 보장용'' AFTER revoked_at',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- 기존 이름의 인덱스가 새 generated column 구성이 아니면 제거한다.
SET @ct_perm_index_columns := (
    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_user_permission'
       AND INDEX_NAME = 'uk_admin_user_perm_active'
);
SET @ct_perm_index_non_unique := (
    SELECT MAX(NON_UNIQUE)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_user_permission'
       AND INDEX_NAME = 'uk_admin_user_perm_active'
);
SET @ct_perm_index_correct := IF(
    COALESCE(@ct_perm_index_columns, '') = 'user_id,permission_code,active_assignment_key'
        AND COALESCE(@ct_perm_index_non_unique, 1) = 0,
    1, 0
);
SET @ct_ddl := IF(
    @ct_perm_index_correct = 0 AND @ct_perm_index_columns IS NOT NULL,
    'ALTER TABLE admin_user_permission DROP INDEX uk_admin_user_perm_active',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_group_index_columns := (
    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_user_group'
       AND INDEX_NAME = 'uk_admin_user_group_active'
);
SET @ct_group_index_non_unique := (
    SELECT MAX(NON_UNIQUE)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_user_group'
       AND INDEX_NAME = 'uk_admin_user_group_active'
);
SET @ct_group_index_correct := IF(
    COALESCE(@ct_group_index_columns, '') = 'user_id,group_code,active_assignment_key'
        AND COALESCE(@ct_group_index_non_unique, 1) = 0,
    1, 0
);
SET @ct_ddl := IF(
    @ct_group_index_correct = 0 AND @ct_group_index_columns IS NOT NULL,
    'ALTER TABLE admin_user_group DROP INDEX uk_admin_user_group_active',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- 중복은 최신 id만 활성으로 유지한다. 기존 인덱스를 먼저 제거했으므로 동일 시각 회수도 충돌하지 않는다.
UPDATE admin_user_permission aup
JOIN ct_duplicate_active_permissions duplicated ON duplicated.id = aup.id
   SET aup.revoked_at = NOW()
 WHERE aup.revoked_at IS NULL;

UPDATE admin_user_group aug
JOIN ct_duplicate_active_groups duplicated ON duplicated.id = aug.id
   SET aug.revoked_at = NOW()
 WHERE aug.revoked_at IS NULL;

-- 중복 자동 정리를 감사에 남긴다. 임시 목록이 비는 재실행에서는 추가 행이 생기지 않는다.
INSERT INTO admin_permission_audit (
    actor_user_id, target_user_id, action_type, permission_code, group_code, reason
)
SELECT NULL,
       duplicated.user_id,
       'DUPLICATE_ACTIVE_PERMISSION_REVOKED',
       duplicated.permission_code,
       NULL,
       CONCAT('중복 활성 직접 권한 assignment id=', duplicated.id, ' 자동 회수')
  FROM ct_duplicate_active_permissions duplicated;

INSERT INTO admin_permission_audit (
    actor_user_id, target_user_id, action_type, permission_code, group_code, reason
)
SELECT NULL,
       duplicated.user_id,
       'DUPLICATE_ACTIVE_GROUP_REVOKED',
       NULL,
       duplicated.group_code,
       CONCAT('중복 활성 그룹 assignment id=', duplicated.id, ' 자동 회수')
  FROM ct_duplicate_active_groups duplicated;

-- 새 UNIQUE 인덱스가 없을 때만 생성한다.
SET @ct_ddl := IF(
    @ct_perm_index_correct = 1,
    'SELECT 1',
    'ALTER TABLE admin_user_permission ADD UNIQUE KEY uk_admin_user_perm_active (user_id, permission_code, active_assignment_key)'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_ddl := IF(
    @ct_group_index_correct = 1,
    'SELECT 1',
    'ALTER TABLE admin_user_group ADD UNIQUE KEY uk_admin_user_group_active (user_id, group_code, active_assignment_key)'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- 적용 후 검증: duplicate_set_count와 non_unique는 모두 0, index_columns는 active_assignment_key로 끝나야 한다.
SELECT 'admin_user_permission' AS table_name,
       (SELECT COUNT(*)
          FROM (
              SELECT user_id, permission_code
                FROM admin_user_permission
               WHERE revoked_at IS NULL
               GROUP BY user_id, permission_code
              HAVING COUNT(*) > 1
          ) duplicate_sets) AS duplicate_set_count,
       (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
          FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'admin_user_permission'
           AND INDEX_NAME = 'uk_admin_user_perm_active') AS index_columns,
       (SELECT MAX(NON_UNIQUE)
          FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'admin_user_permission'
           AND INDEX_NAME = 'uk_admin_user_perm_active') AS non_unique
UNION ALL
SELECT 'admin_user_group',
       (SELECT COUNT(*)
          FROM (
              SELECT user_id, group_code
                FROM admin_user_group
               WHERE revoked_at IS NULL
               GROUP BY user_id, group_code
              HAVING COUNT(*) > 1
          ) duplicate_sets),
       (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
          FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'admin_user_group'
           AND INDEX_NAME = 'uk_admin_user_group_active'),
       (SELECT MAX(NON_UNIQUE)
          FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'admin_user_group'
           AND INDEX_NAME = 'uk_admin_user_group_active');

DROP TEMPORARY TABLE IF EXISTS ct_duplicate_active_permissions;
DROP TEMPORARY TABLE IF EXISTS ct_duplicate_active_groups;
