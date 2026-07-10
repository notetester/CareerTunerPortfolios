export type PermissionGroupCode =
  | "MEMBER_ADMIN"
  | "AI_ADMIN"
  | "BILLING_ADMIN"
  | "CONTENT_ADMIN"
  | "AUDIT_ADMIN"
  | "POLICY_ADMIN";

export interface AdminRoutePolicy {
  permissionGroups?: readonly PermissionGroupCode[];
  superOnly?: boolean;
}

export type AdminPermissionStatus = "idle" | "loading" | "ready" | "error";
export type AdminAccessDecision = "loading" | "anonymous" | "forbidden" | "allowed";

const PERMISSION_CODE_TO_GROUPS: Readonly<Record<string, readonly PermissionGroupCode[]>> = {
  MEMBER_ADMIN: ["MEMBER_ADMIN"],
  USER_READ: ["MEMBER_ADMIN"],
  USER_STATUS_WRITE: ["MEMBER_ADMIN"],
  BLOCK_MANAGE: ["MEMBER_ADMIN"],
  PROFILE_READ: ["MEMBER_ADMIN"],
  CONSENT_READ: ["MEMBER_ADMIN"],
  AI_ADMIN: ["AI_ADMIN"],
  AI_USAGE_READ: ["AI_ADMIN"],
  AI_OPERATION_MANAGE: ["AI_ADMIN"],
  ANALYSIS_READ: ["AI_ADMIN"],
  INTERVIEW_READ: ["AI_ADMIN"],
  BILLING_ADMIN: ["BILLING_ADMIN"],
  BILLING_READ: ["BILLING_ADMIN"],
  BILLING_WRITE: ["BILLING_ADMIN"],
  CONTENT_ADMIN: ["CONTENT_ADMIN"],
  CONTENT_MANAGE: ["CONTENT_ADMIN"],
  AUDIT_ADMIN: ["AUDIT_ADMIN"],
  SECURITY_LOG_READ: ["AUDIT_ADMIN"],
  EMAIL_AUDIT_READ: ["AUDIT_ADMIN"],
  ADMIN_AUDIT_READ: ["AUDIT_ADMIN"],
  POLICY_ADMIN: ["POLICY_ADMIN"],
  POLICY_MANAGE: ["POLICY_ADMIN"],
  ADMIN_PERMISSION_MANAGE: ["POLICY_ADMIN"],
};

/**
 * 관리자 라우트의 단일 접근 정책표다. 라우트와 페이지 셸이 서로 다른 권한 기준을 쓰지 않도록
 * 라우트 생성 시 이 표의 정책을 metadata와 중앙 boundary에 함께 전달한다.
 */
export const ADMIN_ROUTE_POLICIES = {
  admin: {},
  "admin/home": {},
  "admin/dashboard": {},
  "admin/analytics": { permissionGroups: ["AI_ADMIN"] },
  "admin/fit-analysis": { permissionGroups: ["AI_ADMIN"] },
  "admin/prompts/fit-analysis": { permissionGroups: ["AI_ADMIN"] },
  "admin/prompts/analytics": { permissionGroups: ["AI_ADMIN"] },
  "admin/company/applications": { permissionGroups: ["MEMBER_ADMIN"] },
  "admin/company/job-postings": { permissionGroups: ["CONTENT_ADMIN"] },
  "admin/notification-settings": {},
  "admin/ads": { permissionGroups: ["CONTENT_ADMIN"] },
  "admin/users": { permissionGroups: ["MEMBER_ADMIN"] },
  "admin/users/blocked": { permissionGroups: ["MEMBER_ADMIN", "AUDIT_ADMIN"] },
  "admin/security": { permissionGroups: ["AUDIT_ADMIN", "POLICY_ADMIN"] },
  "admin/security/login-risk": { permissionGroups: ["AUDIT_ADMIN", "POLICY_ADMIN"] },
  "admin/security/mfa-policy": { permissionGroups: ["AUDIT_ADMIN", "POLICY_ADMIN"] },
  "admin/audit/security": { permissionGroups: ["AUDIT_ADMIN"] },
  "admin/audit/email": { permissionGroups: ["AUDIT_ADMIN"] },
  "admin/audit/activity": { permissionGroups: ["AUDIT_ADMIN"] },
  "admin/audit/email-log": { permissionGroups: ["AUDIT_ADMIN"] },
  "admin/profiles": { permissionGroups: ["MEMBER_ADMIN"] },
  "admin/consents": { permissionGroups: ["MEMBER_ADMIN"] },
  "admin/super": { permissionGroups: ["POLICY_ADMIN"], superOnly: true },
  "admin/policies": { permissionGroups: ["POLICY_ADMIN"], superOnly: true },
  "admin/runtime-settings": { permissionGroups: ["POLICY_ADMIN"], superOnly: true },
  "admin/action-logs": { permissionGroups: ["AUDIT_ADMIN", "POLICY_ADMIN"] },
  "admin/payments": { permissionGroups: ["BILLING_ADMIN"] },
  "admin/credits": { permissionGroups: ["BILLING_ADMIN"] },
  "admin/rewards": { permissionGroups: ["BILLING_ADMIN"] },
  "admin/staff-grades": { permissionGroups: ["POLICY_ADMIN"], superOnly: true },
  "admin/application-cases": { permissionGroups: ["MEMBER_ADMIN", "AI_ADMIN"] },
  "admin/ai-usage": { permissionGroups: ["AI_ADMIN"] },
  "admin/chatbot-governance": { permissionGroups: ["AI_ADMIN"] },
  "admin/ai-settings": { permissionGroups: ["AI_ADMIN"] },
  "admin/job-analysis": { permissionGroups: ["AI_ADMIN"] },
  "admin/company-analysis": { permissionGroups: ["AI_ADMIN"] },
  "admin/interviews": { permissionGroups: ["AI_ADMIN"] },
  "admin/interview/reports": { permissionGroups: ["AI_ADMIN"] },
  "admin/corrections": { permissionGroups: ["AI_ADMIN"] },
  "admin/interview/knowledge": { permissionGroups: ["AI_ADMIN"] },
  "admin/community": { permissionGroups: ["CONTENT_ADMIN"] },
  "admin/notices": { permissionGroups: ["CONTENT_ADMIN"] },
  "admin/notices/new": { permissionGroups: ["CONTENT_ADMIN"] },
  "admin/faq": { permissionGroups: ["CONTENT_ADMIN"] },
  "admin/faq/new": { permissionGroups: ["CONTENT_ADMIN"] },
  "admin/ai-support": { permissionGroups: ["CONTENT_ADMIN", "AI_ADMIN"] },
  "admin/inquiries": { permissionGroups: ["CONTENT_ADMIN"] },
  "admin/terms": { permissionGroups: ["CONTENT_ADMIN", "POLICY_ADMIN"] },
  "admin/terms/guidelines": { permissionGroups: ["CONTENT_ADMIN", "POLICY_ADMIN"] },
  "admin/notifications": { permissionGroups: ["CONTENT_ADMIN"] },
  "admin/plans": { permissionGroups: ["BILLING_ADMIN"] },
  "admin/prompts": { permissionGroups: ["AI_ADMIN"] },
  "admin/prompts/profile": { permissionGroups: ["AI_ADMIN"] },
  "admin/prompts/interview": { permissionGroups: ["AI_ADMIN"] },
  "admin/logs": { permissionGroups: ["AUDIT_ADMIN"] },
} as const satisfies Record<string, AdminRoutePolicy>;

export type AdminRoutePath = keyof typeof ADMIN_ROUTE_POLICIES;

export function adminRoutePolicy(path: AdminRoutePath): AdminRoutePolicy {
  return ADMIN_ROUTE_POLICIES[path];
}

export function isAdminRole(role: string | null | undefined): boolean {
  return role === "ADMIN" || role === "SUPER_ADMIN";
}

export function adminLoginRedirect(pathname: string, search: string): string {
  return `/login?returnTo=${encodeURIComponent(`${pathname}${search}`)}`;
}

export function permissionGroupsFromCodes(codes: readonly string[]): Set<PermissionGroupCode> {
  return new Set(
    codes
      .flatMap((code) => PERMISSION_CODE_TO_GROUPS[code] ?? [])
      .filter(isPermissionGroupCode),
  );
}

export function resolveAdminRouteAccess({
  authLoading,
  hasUser,
  role,
  policy,
  permissionStatus,
  grantedGroups,
}: {
  authLoading: boolean;
  hasUser: boolean;
  role: string | null | undefined;
  policy: AdminRoutePolicy;
  permissionStatus: AdminPermissionStatus;
  grantedGroups: ReadonlySet<PermissionGroupCode>;
}): AdminAccessDecision {
  if (authLoading) return "loading";
  if (!hasUser) return "anonymous";
  if (!isAdminRole(role)) return "forbidden";
  if (role === "SUPER_ADMIN") return "allowed";
  if (policy.superOnly) return "forbidden";

  const requiredGroups = policy.permissionGroups ?? [];
  if (requiredGroups.length === 0) return "allowed";
  if (permissionStatus === "idle" || permissionStatus === "loading") return "loading";
  if (permissionStatus !== "ready") return "forbidden";
  return requiredGroups.some((group) => grantedGroups.has(group)) ? "allowed" : "forbidden";
}

/** mock API도 화면 guard와 별개로 `/admin/**` 요청을 중앙에서 한 번 더 닫는다. */
export function canAccessMockApi(path: string, role: string | null | undefined): boolean {
  const pathname = path.split("?", 1)[0];
  if (pathname !== "/admin" && !pathname.startsWith("/admin/")) return true;
  if (!isAdminRole(role)) return false;
  if (role === "SUPER_ADMIN") return true;
  return ![
    "/admin/super",
    "/admin/policies",
    "/admin/runtime-settings",
    "/admin/staff-grades",
  ].some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

function isPermissionGroupCode(value: string): value is PermissionGroupCode {
  return ["MEMBER_ADMIN", "AI_ADMIN", "BILLING_ADMIN", "CONTENT_ADMIN", "AUDIT_ADMIN", "POLICY_ADMIN"].includes(value);
}
