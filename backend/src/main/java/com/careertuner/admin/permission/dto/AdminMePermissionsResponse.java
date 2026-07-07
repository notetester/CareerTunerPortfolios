package com.careertuner.admin.permission.dto;

import java.util.List;

/**
 * GET /api/admin/me/permissions 응답.
 *
 * @param role        현재 계정 role (ADMIN/SUPER_ADMIN)
 * @param superAdmin  SUPER_ADMIN 여부(전체 메뉴 노출)
 * @param permissions 실효 권한 코드 목록(SUPER_ADMIN 도 조회값 그대로 — 프런트는 superAdmin 우선)
 */
public record AdminMePermissionsResponse(String role, boolean superAdmin, List<String> permissions) {
}
