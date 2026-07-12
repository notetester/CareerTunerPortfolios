import { useCallback, useMemo } from "react";

import { useAuth } from "@/app/auth/AuthContext";
import { useAdminPermissions } from "@/admin/hooks/useAdminPermissions";
import {
  adminPermissionCode,
  hasAnyAdminPermission,
  type AdminPermissionDomain,
  type AdminPermissionCode,
  type AdminPermissionStatus,
} from "./adminAccess";

export interface AdminAuthorization {
  role: string | null;
  status: AdminPermissionStatus;
  grantedPermissions: ReadonlySet<string>;
  can: (...requiredPermissions: AdminPermissionCode[]) => boolean;
}

export interface AdminDomainAuthorization extends AdminAuthorization {
  canRead: boolean;
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
}

/**
 * 화면 내부의 버튼·폼·하위 탭이 라우트와 동일한 실효 권한 응답을 사용하게 한다.
 * SUPER_ADMIN은 API 계약과 동일하게 전체 허용하고, 일반 ADMIN은 조회 완료 전까지 fail-closed다.
 */
export function useAdminAuthorization(): AdminAuthorization {
  const { user } = useAuth();
  const role = user?.role ?? null;
  const permissionState = useAdminPermissions(user?.id ?? null, role, role === "ADMIN");
  const grantedPermissions = useMemo<ReadonlySet<string>>(
    () => permissionState.status === "ready" && permissionState.data
      ? new Set(permissionState.data.permissions)
      : new Set<string>(),
    [permissionState.data, permissionState.status],
  );
  const can = useCallback(
    (...requiredPermissions: AdminPermissionCode[]) => (
      hasAnyAdminPermission(role, grantedPermissions, requiredPermissions)
    ),
    [grantedPermissions, role],
  );

  return {
    role,
    status: role === "SUPER_ADMIN" ? "ready" : permissionState.status,
    grantedPermissions,
    can,
  };
}

export function useAdminDomainAuthorization(domain: AdminPermissionDomain): AdminDomainAuthorization {
  const authorization = useAdminAuthorization();
  return {
    ...authorization,
    canRead: authorization.can(adminPermissionCode(domain, "READ")),
    canCreate: authorization.can(adminPermissionCode(domain, "CREATE")),
    canUpdate: authorization.can(adminPermissionCode(domain, "UPDATE")),
    canDelete: authorization.can(adminPermissionCode(domain, "DELETE")),
  };
}
