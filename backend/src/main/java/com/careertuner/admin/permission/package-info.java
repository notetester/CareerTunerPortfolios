/**
 * 관리자 권한 집행 계층.
 *
 * <p>기존 권한 데이터 모델(admin_permission_policy / admin_permission_group(_item) /
 * admin_user_permission / admin_user_group — patches 20260624·20260702)을 기반으로
 * 실효 권한을 계산({@code EffectivePermissionService})하고,
 * {@code @RequireAdminPermission} 어노테이션 + 인터셉터로 컨트롤러 접근을 집행한다.</p>
 *
 * <p>관리자 알림 opt-out(notification_preference.categories_json 의 admin.* 하위 키)과
 * GET /api/admin/me/permissions(프런트 메뉴 제어)도 이 패키지가 담당한다.
 * 권한/그룹 CRUD 는 기존 admin/superadmin 소유 — 여기서는 조회만 한다.</p>
 */
package com.careertuner.admin.permission;
