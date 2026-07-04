-- 관리자 권한 세분화: 메뉴 그룹, 권한 코드, 권한 템플릿 그룹 보강
-- 실행 예시: mysql -h <host> -u <user> -p <db> < 20260702_admin_permission_granular_groups.sql

CREATE TABLE IF NOT EXISTS admin_permission_menu_group (
    menu_group_code VARCHAR(80) PRIMARY KEY COMMENT '관리자 메뉴 그룹 코드. MEMBER/AI/BILLING/CONTENT/AUDIT/POLICY 등',
    display_name    VARCHAR(100) NOT NULL COMMENT '관리자 화면에 표시할 메뉴 그룹명',
    description     VARCHAR(500) NULL COMMENT '해당 메뉴 그룹이 담당하는 운영 범위 설명',
    display_order   INT NOT NULL DEFAULT 0 COMMENT '관리자 사이드바와 권한 관리 화면 표시 순서',
    active          TINYINT(1) NOT NULL DEFAULT 1 COMMENT '메뉴 그룹 사용 여부',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    KEY idx_admin_perm_menu_group_active (active, display_order)
) COMMENT='관리자 권한 코드가 속하는 메뉴 그룹 메타데이터';

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'admin_permission_policy'
      AND COLUMN_NAME = 'menu_group_code'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE admin_permission_policy ADD COLUMN menu_group_code VARCHAR(80) NULL COMMENT ''권한이 속한 관리자 메뉴 그룹 코드. admin_permission_menu_group.menu_group_code 참조용'' AFTER description',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'admin_permission_policy'
      AND COLUMN_NAME = 'display_order'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE admin_permission_policy ADD COLUMN display_order INT NOT NULL DEFAULT 0 COMMENT ''권한 관리 화면 표시 순서'' AFTER menu_group_code',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'admin_permission_group'
      AND COLUMN_NAME = 'role_scope'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE admin_permission_group ADD COLUMN role_scope VARCHAR(20) NOT NULL DEFAULT ''ADMIN'' COMMENT ''권한 그룹을 부여할 수 있는 최소 역할. ADMIN/SUPER_ADMIN'' AFTER description',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'admin_permission_group'
      AND COLUMN_NAME = 'display_order'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE admin_permission_group ADD COLUMN display_order INT NOT NULL DEFAULT 0 COMMENT ''권한 그룹 표시 순서'' AFTER role_scope',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO admin_permission_menu_group (menu_group_code, display_name, description, display_order)
VALUES
('MEMBER', '회원/보안 운영', '회원 관리, 차단 관리, 프로필, 동의, 로그인 보안 감사 메뉴 그룹', 10),
('AI', 'AI/분석 운영', 'AI 사용량, AI 설정, 프롬프트, 공고/기업/면접 분석 운영 메뉴 그룹', 20),
('BILLING', '결제/구독 운영', '결제, 요금제, 구독 운영 메뉴 그룹', 30),
('CONTENT', '콘텐츠/고객지원 운영', '공지, FAQ, 문의, 신고/검수, 약관, 알림 운영 메뉴 그룹', 40),
('AUDIT', '감사/로그 운영', '관리자 활동 로그, 시스템 로그, 로그인/이메일 감사 메뉴 그룹', 50),
('POLICY', '정책/권한 운영', '운영 정책과 관리자 권한 관리 메뉴 그룹', 60)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    display_order = VALUES(display_order),
    active = 1;

INSERT INTO admin_permission_policy (permission_code, display_name, description, menu_group_code, display_order)
VALUES
('MEMBER_ADMIN', '회원 운영 관리자', '회원 관리, 차단 관리, 프로필, 동의 화면을 운영할 수 있는 대표 권한', 'MEMBER', 10),
('AI_ADMIN', 'AI 운영 관리자', 'AI 사용량, AI 설정, 프롬프트, 분석 운영 화면을 운영할 수 있는 대표 권한', 'AI', 20),
('BILLING_ADMIN', '결제 운영 관리자', '결제와 요금제 운영 화면을 관리할 수 있는 대표 권한', 'BILLING', 30),
('CONTENT_ADMIN', '콘텐츠 운영 관리자', '공지, FAQ, 문의, 신고/검수, 약관, 알림 화면을 운영할 수 있는 대표 권한', 'CONTENT', 40),
('AUDIT_ADMIN', '감사 운영 관리자', '로그인/보안/이메일 감사와 관리자 활동 로그를 조회할 수 있는 대표 권한', 'AUDIT', 50),
('POLICY_ADMIN', '정책/권한 관리자', '운영 정책과 관리자 권한 템플릿을 관리할 수 있는 슈퍼 관리자 전용 대표 권한', 'POLICY', 60),
('BLOCK_MANAGE', '차단 관리', '회원 차단, 차단 해제, 차단 만료 시각 변경을 처리하는 권한', 'MEMBER', 11),
('EMAIL_AUDIT_READ', '이메일 감사 조회', '이메일 인증과 비밀번호 재설정 토큰 이력을 조회하는 권한', 'AUDIT', 51),
('ADMIN_AUDIT_READ', '관리자 활동 로그 조회', '관리자 상태 변경, 권한 변경, 정책 변경 이력을 조회하는 권한', 'AUDIT', 52),
('BILLING_READ', '결제 조회', '결제와 구독 내역을 조회하는 권한', 'BILLING', 31),
('BILLING_WRITE', '결제 운영 처리', '요금제, 환불, 구독 상태 변경 등 결제 운영 처리를 수행하는 권한', 'BILLING', 32),
('CONTENT_MANAGE', '콘텐츠/고객지원 관리', '공지, FAQ, 문의, 신고/검수, 약관, 알림을 관리하는 권한', 'CONTENT', 41),
('AI_OPERATION_MANAGE', 'AI 운영 설정 관리', 'AI 설정, 프롬프트, 모델 운영 상태를 관리하는 권한', 'AI', 21),
('ANALYSIS_READ', '분석 결과 조회', '공고/기업/적합도/통계 분석 결과를 조회하는 권한', 'AI', 22),
('INTERVIEW_READ', '면접 운영 조회', '면접 세션, 면접 지식, 면접 AI 실패 이력을 조회하는 권한', 'AI', 23)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    menu_group_code = VALUES(menu_group_code),
    display_order = VALUES(display_order),
    active = 1;

UPDATE admin_permission_policy
SET menu_group_code = 'MEMBER', display_order = 12
WHERE permission_code IN ('USER_READ', 'USER_STATUS_WRITE', 'PROFILE_READ', 'CONSENT_READ');

UPDATE admin_permission_policy
SET menu_group_code = 'AI', display_order = 24
WHERE permission_code = 'AI_USAGE_READ';

UPDATE admin_permission_policy
SET menu_group_code = 'AUDIT', display_order = 53
WHERE permission_code = 'SECURITY_LOG_READ';

UPDATE admin_permission_policy
SET menu_group_code = 'POLICY', display_order = 61
WHERE permission_code IN ('POLICY_MANAGE', 'ADMIN_PERMISSION_MANAGE');

INSERT INTO admin_permission_group (group_code, display_name, description, role_scope, display_order)
VALUES
('MEMBER_ADMIN', '회원 운영 권한 템플릿', '회원 관리, 차단 관리, 프로필, 동의 운영에 필요한 권한 묶음', 'ADMIN', 10),
('AI_ADMIN', 'AI 운영 권한 템플릿', 'AI 사용량, 설정, 프롬프트, 분석 운영에 필요한 권한 묶음', 'ADMIN', 20),
('BILLING_ADMIN', '결제 운영 권한 템플릿', '결제와 요금제 운영에 필요한 권한 묶음', 'ADMIN', 30),
('CONTENT_ADMIN', '콘텐츠 운영 권한 템플릿', '공지, FAQ, 문의, 신고/검수, 약관, 알림 운영에 필요한 권한 묶음', 'ADMIN', 40),
('AUDIT_ADMIN', '감사 운영 권한 템플릿', '로그인/보안/이메일 감사와 관리자 활동 로그 조회에 필요한 권한 묶음', 'ADMIN', 50),
('POLICY_ADMIN', '정책/권한 운영 권한 템플릿', '운영 정책과 관리자 권한 템플릿을 관리하는 슈퍼 관리자 전용 권한 묶음', 'SUPER_ADMIN', 60)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    role_scope = VALUES(role_scope),
    display_order = VALUES(display_order),
    active = 1;

INSERT IGNORE INTO admin_permission_group_item (group_code, permission_code)
VALUES
('MEMBER_ADMIN', 'MEMBER_ADMIN'),
('MEMBER_ADMIN', 'USER_READ'),
('MEMBER_ADMIN', 'USER_STATUS_WRITE'),
('MEMBER_ADMIN', 'BLOCK_MANAGE'),
('MEMBER_ADMIN', 'PROFILE_READ'),
('MEMBER_ADMIN', 'CONSENT_READ'),
('AI_ADMIN', 'AI_ADMIN'),
('AI_ADMIN', 'AI_USAGE_READ'),
('AI_ADMIN', 'AI_OPERATION_MANAGE'),
('AI_ADMIN', 'ANALYSIS_READ'),
('AI_ADMIN', 'INTERVIEW_READ'),
('BILLING_ADMIN', 'BILLING_ADMIN'),
('BILLING_ADMIN', 'BILLING_READ'),
('BILLING_ADMIN', 'BILLING_WRITE'),
('CONTENT_ADMIN', 'CONTENT_ADMIN'),
('CONTENT_ADMIN', 'CONTENT_MANAGE'),
('AUDIT_ADMIN', 'AUDIT_ADMIN'),
('AUDIT_ADMIN', 'SECURITY_LOG_READ'),
('AUDIT_ADMIN', 'EMAIL_AUDIT_READ'),
('AUDIT_ADMIN', 'ADMIN_AUDIT_READ'),
('POLICY_ADMIN', 'POLICY_ADMIN'),
('POLICY_ADMIN', 'POLICY_MANAGE'),
('POLICY_ADMIN', 'ADMIN_PERMISSION_MANAGE'),
('ADMIN_OPERATOR', 'MEMBER_ADMIN'),
('ADMIN_OPERATOR', 'AI_ADMIN'),
('ADMIN_OPERATOR', 'BILLING_ADMIN'),
('ADMIN_OPERATOR', 'CONTENT_ADMIN'),
('SECURITY_OPERATOR', 'AUDIT_ADMIN'),
('SECURITY_OPERATOR', 'BLOCK_MANAGE'),
('SECURITY_OPERATOR', 'EMAIL_AUDIT_READ'),
('SECURITY_OPERATOR', 'ADMIN_AUDIT_READ'),
('SUPER_ADMIN_GROUP', 'MEMBER_ADMIN'),
('SUPER_ADMIN_GROUP', 'AI_ADMIN'),
('SUPER_ADMIN_GROUP', 'BILLING_ADMIN'),
('SUPER_ADMIN_GROUP', 'CONTENT_ADMIN'),
('SUPER_ADMIN_GROUP', 'AUDIT_ADMIN'),
('SUPER_ADMIN_GROUP', 'POLICY_ADMIN'),
('SUPER_ADMIN_GROUP', 'BLOCK_MANAGE'),
('SUPER_ADMIN_GROUP', 'EMAIL_AUDIT_READ'),
('SUPER_ADMIN_GROUP', 'ADMIN_AUDIT_READ'),
('SUPER_ADMIN_GROUP', 'BILLING_READ'),
('SUPER_ADMIN_GROUP', 'BILLING_WRITE'),
('SUPER_ADMIN_GROUP', 'CONTENT_MANAGE'),
('SUPER_ADMIN_GROUP', 'AI_OPERATION_MANAGE'),
('SUPER_ADMIN_GROUP', 'ANALYSIS_READ'),
('SUPER_ADMIN_GROUP', 'INTERVIEW_READ');
