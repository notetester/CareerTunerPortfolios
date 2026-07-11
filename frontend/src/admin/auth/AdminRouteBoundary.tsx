import type { ReactNode } from "react";
import { Link, Navigate, useLocation } from "react-router";

import { useAuth } from "@/app/auth/AuthContext";
import { PageFallback } from "@/app/pages/pageFallback";
import { useAdminPermissions } from "@/admin/hooks/useAdminPermissions";
import {
  adminLoginRedirect,
  permissionGroupsFromCodes,
  resolveAdminRouteAccess,
  type AdminRoutePolicy,
  type PermissionGroupCode,
} from "./adminAccess";

export function AdminRouteBoundary({
  policy,
  render,
}: {
  policy: AdminRoutePolicy;
  render: () => ReactNode;
}) {
  const location = useLocation();
  const { user, loading: authLoading } = useAuth();
  const needsPermissions =
    user?.role === "ADMIN"
    && !policy.superOnly
    && Boolean(policy.permissionGroups && policy.permissionGroups.length > 0);
  const permissions = useAdminPermissions(user?.id ?? null, user?.role ?? null, needsPermissions);
  const grantedGroups = permissions.status === "ready" && permissions.data
    ? permissionGroupsFromCodes(permissions.data.permissions)
    : new Set<PermissionGroupCode>();
  const decision = resolveAdminRouteAccess({
    authLoading,
    hasUser: Boolean(user),
    role: user?.role,
    policy,
    permissionStatus: permissions.status,
    grantedGroups,
  });

  if (decision === "loading") return <PageFallback />;
  if (decision === "anonymous") {
    return <Navigate to={adminLoginRedirect(location.pathname, location.search)} replace />;
  }
  if (decision === "forbidden") {
    const permissionLookupFailed = needsPermissions && permissions.status === "error";
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4 dark:bg-slate-950">
        <section
          role="alert"
          className="w-full max-w-lg rounded-2xl border border-slate-200 bg-card p-8 text-center shadow-sm dark:border-slate-800"
        >
          <div className="text-sm font-black tracking-[0.22em] text-red-600">403</div>
          <h1 className="mt-3 text-2xl font-black text-foreground">관리자 권한이 필요합니다</h1>
          <p className="mt-3 text-sm leading-6 text-muted-foreground">
            {permissionLookupFailed
              ? "관리자 권한 정보를 확인하지 못해 안전하게 접근을 차단했습니다. 잠시 후 다시 시도해 주세요."
              : "현재 계정은 이 관리자 화면을 사용할 수 없습니다."}
          </p>
          <Link
            to="/dashboard"
            className="mt-6 inline-flex h-10 items-center justify-center rounded-lg bg-primary px-5 text-sm font-bold text-primary-foreground"
          >
            일반 대시보드로 이동
          </Link>
        </section>
      </main>
    );
  }

  return <>{render()}</>;
}
