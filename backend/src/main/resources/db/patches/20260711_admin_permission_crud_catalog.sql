-- 관리자 CRUD 권한 카탈로그와 runtime 권한 그룹 템플릿 정의.
--
-- 적용 순서: 20260711_admin_soft_delete_columns.sql 이후 실행한다.
-- legacy permission/group row는 호환을 위해 삭제하거나 비활성화하지 않는다.
-- 이 패치는 runtime이 읽는 카탈로그와 그룹 항목만 선언하며 기존 사용자의 직접 권한은 변경하지 않는다.

SET @ct_group_item_deleted_at_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_permission_group_item'
       AND COLUMN_NAME = 'deleted_at'
);
DROP TEMPORARY TABLE IF EXISTS ct_admin_crud_catalog_guard;

-- canonical 관계 행 자체는 운영 중 soft-delete되어도 남으므로, 전체 관계가 이미 존재하면
-- 이 패치가 한 번 완료된 것으로 본다. 이후 재실행은 active/deleted_at 운영 상태를 보존한다.
DROP TEMPORARY TABLE IF EXISTS ct_admin_crud_canonical_group_item;
CREATE TEMPORARY TABLE ct_admin_crud_canonical_group_item (
    group_code VARCHAR(80) NOT NULL,
    permission_code VARCHAR(80) NOT NULL,
    PRIMARY KEY (group_code, permission_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO ct_admin_crud_canonical_group_item (group_code, permission_code)
VALUES
    ('MEMBER_ADMIN', 'USER_READ'),
    ('MEMBER_ADMIN', 'USER_CREATE'),
    ('MEMBER_ADMIN', 'USER_UPDATE'),
    ('SECURITY_OPERATOR', 'SECURITY_READ'),
    ('SECURITY_OPERATOR', 'SECURITY_CREATE'),
    ('SECURITY_OPERATOR', 'SECURITY_UPDATE'),
    ('BILLING_ADMIN', 'BILLING_READ'),
    ('BILLING_ADMIN', 'BILLING_CREATE'),
    ('BILLING_ADMIN', 'BILLING_UPDATE'),
    ('CONTENT_ADMIN', 'CONTENT_READ'),
    ('CONTENT_ADMIN', 'CONTENT_CREATE'),
    ('CONTENT_ADMIN', 'CONTENT_UPDATE'),
    ('AI_ADMIN', 'AI_READ'),
    ('AI_ADMIN', 'AI_CREATE'),
    ('AI_ADMIN', 'AI_UPDATE'),
    ('AUDIT_ADMIN', 'AUDIT_READ'),
    ('POLICY_ADMIN', 'POLICY_READ'),
    ('POLICY_ADMIN', 'POLICY_CREATE'),
    ('POLICY_ADMIN', 'POLICY_UPDATE'),
    ('POLICY_ADMIN', 'POLICY_DELETE'),
    ('POLICY_ADMIN', 'ADMIN_PERMISSION_READ'),
    ('POLICY_ADMIN', 'ADMIN_PERMISSION_CREATE'),
    ('POLICY_ADMIN', 'ADMIN_PERMISSION_UPDATE'),
    ('POLICY_ADMIN', 'ADMIN_PERMISSION_DELETE'),
    ('SUPER_ADMIN_GROUP', 'USER_READ'),
    ('SUPER_ADMIN_GROUP', 'USER_CREATE'),
    ('SUPER_ADMIN_GROUP', 'USER_UPDATE'),
    ('SUPER_ADMIN_GROUP', 'USER_DELETE'),
    ('SUPER_ADMIN_GROUP', 'SECURITY_READ'),
    ('SUPER_ADMIN_GROUP', 'SECURITY_CREATE'),
    ('SUPER_ADMIN_GROUP', 'SECURITY_UPDATE'),
    ('SUPER_ADMIN_GROUP', 'SECURITY_DELETE'),
    ('SUPER_ADMIN_GROUP', 'BILLING_READ'),
    ('SUPER_ADMIN_GROUP', 'BILLING_CREATE'),
    ('SUPER_ADMIN_GROUP', 'BILLING_UPDATE'),
    ('SUPER_ADMIN_GROUP', 'BILLING_DELETE'),
    ('SUPER_ADMIN_GROUP', 'CONTENT_READ'),
    ('SUPER_ADMIN_GROUP', 'CONTENT_CREATE'),
    ('SUPER_ADMIN_GROUP', 'CONTENT_UPDATE'),
    ('SUPER_ADMIN_GROUP', 'CONTENT_DELETE'),
    ('SUPER_ADMIN_GROUP', 'AI_READ'),
    ('SUPER_ADMIN_GROUP', 'AI_CREATE'),
    ('SUPER_ADMIN_GROUP', 'AI_UPDATE'),
    ('SUPER_ADMIN_GROUP', 'AI_DELETE'),
    ('SUPER_ADMIN_GROUP', 'POLICY_READ'),
    ('SUPER_ADMIN_GROUP', 'POLICY_CREATE'),
    ('SUPER_ADMIN_GROUP', 'POLICY_UPDATE'),
    ('SUPER_ADMIN_GROUP', 'POLICY_DELETE'),
    ('SUPER_ADMIN_GROUP', 'ADMIN_PERMISSION_READ'),
    ('SUPER_ADMIN_GROUP', 'ADMIN_PERMISSION_CREATE'),
    ('SUPER_ADMIN_GROUP', 'ADMIN_PERMISSION_UPDATE'),
    ('SUPER_ADMIN_GROUP', 'ADMIN_PERMISSION_DELETE'),
    ('SUPER_ADMIN_GROUP', 'AUDIT_READ');

SET @ct_catalog_initialized := IF(
    (SELECT COUNT(*)
       FROM admin_permission_menu_group
      WHERE menu_group_code IN ('MEMBER', 'AI', 'BILLING', 'CONTENT', 'AUDIT', 'POLICY')) = 6
    AND NOT EXISTS (
        SELECT 1
          FROM ct_admin_crud_canonical_group_item canonical
          LEFT JOIN admin_permission_group_item stored_item
            ON stored_item.group_code = canonical.group_code
           AND stored_item.permission_code = canonical.permission_code
         WHERE stored_item.id IS NULL
    ),
    1,
    0
);
CREATE TEMPORARY TABLE ct_admin_crud_catalog_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);
INSERT INTO ct_admin_crud_catalog_guard (guard_ok)
VALUES (IF(@ct_group_item_deleted_at_exists = 1, 1, 0));

INSERT INTO admin_permission_menu_group (
    menu_group_code, display_name, description, display_order, active
)
VALUES
    ('MEMBER', '회원 운영', '회원 계정과 프로필 운영 권한', 10, 1),
    ('AI', 'AI 운영', 'AI 분석과 모델 운영 권한', 20, 1),
    ('BILLING', '결제 운영', '결제와 구독 운영 권한', 30, 1),
    ('CONTENT', '콘텐츠 운영', '콘텐츠와 고객지원 운영 권한', 40, 1),
    ('AUDIT', '보안·감사 운영', '보안 설정과 감사 로그 운영 권한', 50, 1),
    ('POLICY', '정책·권한 운영', '운영 정책과 관리자 권한 운영', 60, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    display_order = VALUES(display_order),
    active = IF(@ct_catalog_initialized = 1, active, VALUES(active));

INSERT INTO admin_permission_policy (
    permission_code, display_name, description, menu_group_code, display_order, active
)
VALUES
    ('USER_READ', '회원 조회', '회원 데이터를 조회하는 권한.', 'MEMBER', 100, 1),
    ('USER_CREATE', '회원 생성', '회원 데이터를 생성하는 권한.', 'MEMBER', 101, 1),
    ('USER_UPDATE', '회원 수정', '회원 데이터를 수정하는 권한.', 'MEMBER', 102, 1),
    ('USER_DELETE', '회원 삭제', '회원 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'MEMBER', 103, 1),
    ('SECURITY_READ', '보안 조회', '보안 데이터를 조회하는 권한.', 'AUDIT', 200, 1),
    ('SECURITY_CREATE', '보안 생성', '보안 데이터를 생성하는 권한.', 'AUDIT', 201, 1),
    ('SECURITY_UPDATE', '보안 수정', '보안 데이터를 수정하는 권한.', 'AUDIT', 202, 1),
    ('SECURITY_DELETE', '보안 삭제', '보안 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'AUDIT', 203, 1),
    ('BILLING_READ', '결제 조회', '결제 데이터를 조회하는 권한.', 'BILLING', 300, 1),
    ('BILLING_CREATE', '결제 생성', '결제 데이터를 생성하는 권한.', 'BILLING', 301, 1),
    ('BILLING_UPDATE', '결제 수정', '결제 데이터를 수정하는 권한.', 'BILLING', 302, 1),
    ('BILLING_DELETE', '결제 삭제', '결제 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'BILLING', 303, 1),
    ('CONTENT_READ', '콘텐츠 조회', '콘텐츠 데이터를 조회하는 권한.', 'CONTENT', 400, 1),
    ('CONTENT_CREATE', '콘텐츠 생성', '콘텐츠 데이터를 생성하는 권한.', 'CONTENT', 401, 1),
    ('CONTENT_UPDATE', '콘텐츠 수정', '콘텐츠 데이터를 수정하는 권한.', 'CONTENT', 402, 1),
    ('CONTENT_DELETE', '콘텐츠 삭제', '콘텐츠 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'CONTENT', 403, 1),
    ('AI_READ', 'AI 운영 조회', 'AI 운영 데이터를 조회하는 권한.', 'AI', 500, 1),
    ('AI_CREATE', 'AI 운영 생성', 'AI 운영 데이터를 생성하는 권한.', 'AI', 501, 1),
    ('AI_UPDATE', 'AI 운영 수정', 'AI 운영 데이터를 수정하는 권한.', 'AI', 502, 1),
    ('AI_DELETE', 'AI 운영 삭제', 'AI 운영 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'AI', 503, 1),
    ('POLICY_READ', '정책 조회', '정책 데이터를 조회하는 권한.', 'POLICY', 600, 1),
    ('POLICY_CREATE', '정책 생성', '정책 데이터를 생성하는 권한.', 'POLICY', 601, 1),
    ('POLICY_UPDATE', '정책 수정', '정책 데이터를 수정하는 권한.', 'POLICY', 602, 1),
    ('POLICY_DELETE', '정책 삭제', '정책 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'POLICY', 603, 1),
    ('ADMIN_PERMISSION_READ', '관리자 권한 조회', '관리자 권한 데이터를 조회하는 권한.', 'POLICY', 700, 1),
    ('ADMIN_PERMISSION_CREATE', '관리자 권한 생성', '관리자 권한 데이터를 생성하는 권한.', 'POLICY', 701, 1),
    ('ADMIN_PERMISSION_UPDATE', '관리자 권한 수정', '관리자 권한 데이터를 수정하는 권한.', 'POLICY', 702, 1),
    ('ADMIN_PERMISSION_DELETE', '관리자 권한 삭제', '관리자 권한 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'POLICY', 703, 1),
    ('AUDIT_READ', '감사 로그 조회', '관리자 및 보안 감사 로그를 조회하는 권한.', 'AUDIT', 800, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    menu_group_code = VALUES(menu_group_code),
    display_order = VALUES(display_order),
    active = IF(@ct_catalog_initialized = 1, active, VALUES(active));

INSERT INTO admin_permission_group (
    group_code, display_name, description, role_scope, display_order, active
)
VALUES
    ('MEMBER_ADMIN', '회원 운영 권한 템플릿', '회원 데이터 조회·생성·수정 기본 권한', 'ADMIN', 10, 1),
    ('SECURITY_OPERATOR', '보안 운영 권한 템플릿', '보안 데이터 조회·생성·수정 기본 권한', 'ADMIN', 20, 1),
    ('BILLING_ADMIN', '결제 운영 권한 템플릿', '결제 데이터 조회·생성·수정 기본 권한', 'ADMIN', 30, 1),
    ('CONTENT_ADMIN', '콘텐츠 운영 권한 템플릿', '콘텐츠 데이터 조회·생성·수정 기본 권한', 'ADMIN', 40, 1),
    ('AI_ADMIN', 'AI 운영 권한 템플릿', 'AI 운영 데이터 조회·생성·수정 기본 권한', 'ADMIN', 50, 1),
    ('AUDIT_ADMIN', '감사 조회 권한 템플릿', '감사 로그 조회 전용 권한', 'ADMIN', 60, 1),
    ('POLICY_ADMIN', '정책·권한 운영 템플릿', '정책과 관리자 권한 전체 CRUD를 수행하는 슈퍼 관리자 전용 권한', 'SUPER_ADMIN', 70, 1),
    ('SUPER_ADMIN_GROUP', '슈퍼 관리자 그룹', '모든 관리자 CRUD와 감사 조회 권한', 'SUPER_ADMIN', 100, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    role_scope = VALUES(role_scope),
    display_order = VALUES(display_order),
    active = IF(@ct_catalog_initialized = 1, active, VALUES(active));

-- 이전 그룹 정의에서 넘어온 exact code가 새 템플릿 권한을 넓히지 않도록 허용 집합 밖 항목을 회수한다.
-- legacy code는 직전 버전 롤백 호환을 위해 그대로 두되 새 컨트롤러 선언에는 사용하지 않는다.
UPDATE admin_permission_group_item group_item
LEFT JOIN ct_admin_crud_canonical_group_item canonical
  ON canonical.group_code = group_item.group_code
 AND canonical.permission_code = group_item.permission_code
   SET group_item.deleted_at = COALESCE(group_item.deleted_at, NOW())
 WHERE @ct_catalog_initialized = 0
   AND group_item.deleted_at IS NULL
   AND group_item.group_code IN (
       'MEMBER_ADMIN', 'SECURITY_OPERATOR', 'BILLING_ADMIN', 'CONTENT_ADMIN',
       'AI_ADMIN', 'AUDIT_ADMIN', 'POLICY_ADMIN', 'SUPER_ADMIN_GROUP'
   )
   AND group_item.permission_code REGEXP
       '^(USER|SECURITY|BILLING|CONTENT|AI|POLICY|ADMIN_PERMISSION)_(READ|CREATE|UPDATE|DELETE)$|^AUDIT_READ$'
   AND canonical.permission_code IS NULL;

-- UNIQUE 관계의 기존 행이 soft-delete 상태이면 새 행을 만들지 않고 복원한다.
INSERT INTO admin_permission_group_item (group_code, permission_code, deleted_at)
SELECT canonical.group_code, canonical.permission_code, NULL
  FROM ct_admin_crud_canonical_group_item canonical
 WHERE 1 = 1
ON DUPLICATE KEY UPDATE
    deleted_at = IF(@ct_catalog_initialized = 1, deleted_at, NULL);

-- exact catalog 29개와 runtime 그룹 템플릿을 검증한다.
SELECT COUNT(*) AS exact_permission_total_count,
       COALESCE(SUM(active = 1), 0) AS exact_permission_active_count,
       @ct_catalog_initialized AS catalog_initialized_before_patch
  FROM admin_permission_policy
 WHERE permission_code IN (
     'USER_READ',
     'USER_CREATE',
     'USER_UPDATE',
     'USER_DELETE',
     'SECURITY_READ',
     'SECURITY_CREATE',
     'SECURITY_UPDATE',
     'SECURITY_DELETE',
     'BILLING_READ',
     'BILLING_CREATE',
     'BILLING_UPDATE',
     'BILLING_DELETE',
     'CONTENT_READ',
     'CONTENT_CREATE',
     'CONTENT_UPDATE',
     'CONTENT_DELETE',
     'AI_READ',
     'AI_CREATE',
     'AI_UPDATE',
     'AI_DELETE',
     'POLICY_READ',
     'POLICY_CREATE',
     'POLICY_UPDATE',
     'POLICY_DELETE',
     'ADMIN_PERMISSION_READ',
     'ADMIN_PERMISSION_CREATE',
     'ADMIN_PERMISSION_UPDATE',
     'ADMIN_PERMISSION_DELETE',
     'AUDIT_READ'
 );

SELECT COUNT(*) AS permission_group_total_count,
       COALESCE(SUM(active = 1), 0) AS permission_group_active_count
  FROM admin_permission_group
 WHERE group_code IN (
     'MEMBER_ADMIN', 'SECURITY_OPERATOR', 'BILLING_ADMIN', 'CONTENT_ADMIN',
     'AI_ADMIN', 'AUDIT_ADMIN', 'POLICY_ADMIN', 'SUPER_ADMIN_GROUP'
 );

SELECT canonical.group_code,
       COUNT(*) AS canonical_expected_item_count,
       COALESCE(SUM(stored_item.id IS NOT NULL), 0) AS canonical_stored_item_count,
       COALESCE(SUM(stored_item.id IS NOT NULL AND stored_item.deleted_at IS NULL), 0)
           AS canonical_active_item_count,
       COALESCE(SUM(stored_item.id IS NOT NULL AND stored_item.deleted_at IS NOT NULL), 0)
           AS canonical_deleted_item_count,
       (
           SELECT COUNT(*)
             FROM admin_permission_group_item legacy
            WHERE legacy.group_code = canonical.group_code
              AND legacy.deleted_at IS NULL
              AND legacy.permission_code NOT REGEXP
                  '^(USER|SECURITY|BILLING|CONTENT|AI|POLICY|ADMIN_PERMISSION)_(READ|CREATE|UPDATE|DELETE)$|^AUDIT_READ$'
       ) AS retained_legacy_active_item_count
  FROM ct_admin_crud_canonical_group_item canonical
  LEFT JOIN admin_permission_group_item stored_item
    ON stored_item.group_code = canonical.group_code
   AND stored_item.permission_code = canonical.permission_code
 GROUP BY canonical.group_code
 ORDER BY MIN(canonical.group_code);

DROP TEMPORARY TABLE IF EXISTS ct_admin_crud_canonical_group_item;
DROP TEMPORARY TABLE IF EXISTS ct_admin_crud_catalog_guard;
