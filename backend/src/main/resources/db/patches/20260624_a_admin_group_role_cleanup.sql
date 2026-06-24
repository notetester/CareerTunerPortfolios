-- 관리자 권한 그룹을 역할 기준으로 정리한다.
-- A파트 전용 그룹은 제거하고, 일반 관리자 그룹을 추가한다.

DELETE FROM admin_user_group
WHERE group_code = 'A_PART_OPERATOR';

DELETE FROM admin_permission_group_item
WHERE group_code = 'A_PART_OPERATOR';

DELETE FROM admin_permission_group
WHERE group_code = 'A_PART_OPERATOR';

INSERT IGNORE INTO admin_permission_group (group_code, display_name, description)
VALUES ('ADMIN_OPERATOR', '일반 관리자 그룹', '회원/프로필/동의/AI 사용 이력/보안 로그 조회 권한');

INSERT IGNORE INTO admin_permission_group_item (group_code, permission_code)
VALUES
('ADMIN_OPERATOR', 'USER_READ'),
('ADMIN_OPERATOR', 'PROFILE_READ'),
('ADMIN_OPERATOR', 'CONSENT_READ'),
('ADMIN_OPERATOR', 'AI_USAGE_READ'),
('ADMIN_OPERATOR', 'SECURITY_LOG_READ');
