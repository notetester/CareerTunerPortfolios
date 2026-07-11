export const ADMIN_PERMISSION_CODES = [
  "USER_READ",
  "USER_CREATE",
  "USER_UPDATE",
  "USER_DELETE",
  "SECURITY_READ",
  "SECURITY_CREATE",
  "SECURITY_UPDATE",
  "SECURITY_DELETE",
  "BILLING_READ",
  "BILLING_CREATE",
  "BILLING_UPDATE",
  "BILLING_DELETE",
  "CONTENT_READ",
  "CONTENT_CREATE",
  "CONTENT_UPDATE",
  "CONTENT_DELETE",
  "AI_READ",
  "AI_CREATE",
  "AI_UPDATE",
  "AI_DELETE",
  "POLICY_READ",
  "POLICY_CREATE",
  "POLICY_UPDATE",
  "POLICY_DELETE",
  "ADMIN_PERMISSION_READ",
  "ADMIN_PERMISSION_CREATE",
  "ADMIN_PERMISSION_UPDATE",
  "ADMIN_PERMISSION_DELETE",
  "AUDIT_READ",
] as const;

export type AdminPermissionCode = (typeof ADMIN_PERMISSION_CODES)[number];

export const ADMIN_PERMISSION_DOMAINS = [
  { code: "USER", label: "회원" },
  { code: "SECURITY", label: "보안" },
  { code: "BILLING", label: "결제" },
  { code: "CONTENT", label: "콘텐츠" },
  { code: "AI", label: "AI" },
  { code: "POLICY", label: "정책" },
  { code: "ADMIN_PERMISSION", label: "관리자 권한" },
] as const;

export const ADMIN_PERMISSION_ACTIONS = [
  { code: "READ", label: "조회" },
  { code: "CREATE", label: "추가" },
  { code: "UPDATE", label: "수정" },
  { code: "DELETE", label: "삭제" },
] as const;

export type AdminPermissionDomain = (typeof ADMIN_PERMISSION_DOMAINS)[number]["code"];
export type AdminPermissionAction = (typeof ADMIN_PERMISSION_ACTIONS)[number]["code"];

export function adminPermissionCode(
  domain: AdminPermissionDomain,
  action: AdminPermissionAction,
): AdminPermissionCode {
  return `${domain}_${action}` as AdminPermissionCode;
}

export interface AdminRoutePolicy {
  /** 백엔드 @RequireAdminPermission 과 동일하게 하나라도 보유하면 허용한다. */
  permissionCodes?: readonly AdminPermissionCode[];
  superOnly?: boolean;
}

export type AdminPermissionStatus = "idle" | "loading" | "ready" | "error";
export type AdminAccessDecision = "loading" | "anonymous" | "forbidden" | "allowed";

/**
 * 관리자 라우트의 단일 접근 정책표다. 라우트와 페이지 셸이 서로 다른 권한 기준을 쓰지 않도록
 * 라우트 생성 시 이 표의 정책을 metadata와 중앙 boundary에 함께 전달한다.
 */
export const ADMIN_ROUTE_POLICIES = {
  // /admin은 권한별 첫 화면으로 보내는 진입점이다. 하나 이상의 운영 READ 권한이 있어야 한다.
  admin: {
    permissionCodes: [
      "USER_READ",
      "SECURITY_READ",
      "BILLING_READ",
      "CONTENT_READ",
      "AI_READ",
      "POLICY_READ",
      "AUDIT_READ",
    ],
  },
  "admin/home": { permissionCodes: ["AI_READ"] },
  "admin/dashboard": { permissionCodes: ["USER_READ", "AI_READ"] },
  "admin/analytics": { permissionCodes: ["AI_READ"] },
  "admin/fit-analysis": { permissionCodes: ["AI_READ"] },
  "admin/prompts/fit-analysis": { permissionCodes: ["AI_READ"] },
  "admin/prompts/analytics": { permissionCodes: ["AI_READ"] },
  "admin/company/applications": { permissionCodes: ["USER_READ"] },
  "admin/company/job-postings": { permissionCodes: ["CONTENT_READ"] },
  "admin/notification-settings": {},
  "admin/ads": { permissionCodes: ["CONTENT_READ"] },
  "admin/users": { permissionCodes: ["USER_READ"] },
  "admin/users/blocked": { permissionCodes: ["USER_READ"] },
  "admin/security": { permissionCodes: ["SECURITY_READ"] },
  "admin/security/login-risk": { permissionCodes: ["SECURITY_READ"] },
  "admin/security/mfa-policy": { permissionCodes: ["POLICY_READ"] },
  "admin/audit/security": { permissionCodes: ["AUDIT_READ"] },
  "admin/audit/email": { permissionCodes: ["AUDIT_READ"] },
  "admin/audit/activity": { permissionCodes: ["AUDIT_READ"] },
  "admin/audit/email-log": { permissionCodes: ["AUDIT_READ"] },
  "admin/profiles": { permissionCodes: ["USER_READ"] },
  "admin/consents": { permissionCodes: ["USER_READ"] },
  "admin/super": { permissionCodes: ["ADMIN_PERMISSION_READ"], superOnly: true },
  "admin/policies": { permissionCodes: ["POLICY_READ"] },
  "admin/runtime-settings": { permissionCodes: ["POLICY_READ"], superOnly: true },
  "admin/action-logs": { permissionCodes: ["AUDIT_READ"] },
  "admin/payments": { permissionCodes: ["BILLING_READ"] },
  "admin/credits": { permissionCodes: ["BILLING_READ"] },
  "admin/rewards": { permissionCodes: ["BILLING_READ"] },
  "admin/staff-grades": { permissionCodes: ["POLICY_READ"], superOnly: true },
  "admin/application-cases": { permissionCodes: ["USER_READ"] },
  "admin/ai-usage": { permissionCodes: ["AI_READ"] },
  "admin/chatbot-governance": { permissionCodes: ["AI_READ"] },
  "admin/ai-settings": { permissionCodes: ["AI_READ"] },
  "admin/job-analysis": { permissionCodes: ["AI_READ"] },
  "admin/company-analysis": { permissionCodes: ["AI_READ"] },
  "admin/interviews": { permissionCodes: ["AI_READ"] },
  "admin/interview/reports": { permissionCodes: ["AI_READ"] },
  "admin/corrections": { permissionCodes: ["AI_READ"] },
  "admin/interview/knowledge": { permissionCodes: ["AI_READ"] },
  "admin/community": { permissionCodes: ["CONTENT_READ", "AI_READ"] },
  "admin/notices": { permissionCodes: ["CONTENT_READ"] },
  "admin/notices/new": { permissionCodes: ["CONTENT_CREATE"] },
  "admin/faq": { permissionCodes: ["CONTENT_READ"] },
  "admin/faq/new": { permissionCodes: ["CONTENT_CREATE"] },
  "admin/ai-support": { permissionCodes: ["AI_READ"] },
  "admin/inquiries": { permissionCodes: ["CONTENT_READ"] },
  "admin/terms": { permissionCodes: ["POLICY_READ"] },
  "admin/terms/guidelines": { permissionCodes: ["CONTENT_READ"] },
  "admin/notifications": { permissionCodes: ["CONTENT_READ"] },
  "admin/plans": { permissionCodes: ["BILLING_READ"] },
  "admin/prompts": { permissionCodes: ["AI_READ"] },
  "admin/prompts/profile": { permissionCodes: ["AI_READ"] },
  "admin/prompts/interview": { permissionCodes: ["AI_READ"] },
  "admin/logs": { permissionCodes: ["AUDIT_READ"] },
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

export function hasAnyAdminPermission(
  role: string | null | undefined,
  grantedPermissions: ReadonlySet<string>,
  requiredPermissions: readonly AdminPermissionCode[],
): boolean {
  if (role === "SUPER_ADMIN") return true;
  if (role !== "ADMIN") return false;
  return requiredPermissions.length === 0
    || requiredPermissions.some((permission) => grantedPermissions.has(permission));
}

export function canAccessAdminRoute(
  role: string | null | undefined,
  policy: AdminRoutePolicy,
  grantedPermissions: ReadonlySet<string>,
): boolean {
  if (role === "SUPER_ADMIN") return true;
  if (role !== "ADMIN" || policy.superOnly) return false;
  return hasAnyAdminPermission(role, grantedPermissions, policy.permissionCodes ?? []);
}

export function resolveAdminRouteAccess({
  authLoading,
  hasUser,
  role,
  policy,
  permissionStatus,
  grantedPermissions,
}: {
  authLoading: boolean;
  hasUser: boolean;
  role: string | null | undefined;
  policy: AdminRoutePolicy;
  permissionStatus: AdminPermissionStatus;
  grantedPermissions: ReadonlySet<string>;
}): AdminAccessDecision {
  if (authLoading) return "loading";
  if (!hasUser) return "anonymous";
  if (!isAdminRole(role)) return "forbidden";
  if (role === "SUPER_ADMIN") return "allowed";
  if (policy.superOnly) return "forbidden";

  const requiredPermissions = policy.permissionCodes ?? [];
  if (requiredPermissions.length === 0) return "allowed";
  if (permissionStatus === "idle" || permissionStatus === "loading") return "loading";
  if (permissionStatus !== "ready") return "forbidden";
  return canAccessAdminRoute(role, policy, grantedPermissions) ? "allowed" : "forbidden";
}

function isPathWithin(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

function adminMockDomain(pathname: string): AdminPermissionDomain | "AUDIT" | "DASHBOARD" | "ME" | null {
  if (isPathWithin(pathname, "/admin/me") || isPathWithin(pathname, "/admin/notification-settings")) return "ME";
  if (isPathWithin(pathname, "/admin/dashboard")) return "DASHBOARD";
  if (isPathWithin(pathname, "/admin/home")) return "AI";

  if ([
    "/admin/audit",
    "/admin/action-logs",
    "/admin/activity-logs",
    "/admin/email-audit",
    "/admin/logs",
  ].some((prefix) => isPathWithin(pathname, prefix))) return "AUDIT";

  if ([
    "/admin/mfa-policy",
    "/admin/policies",
    "/admin/runtime-settings",
    "/admin/settings",
    "/admin/staff-grades",
    "/admin/legal",
    "/admin/terms",
  ].some((prefix) => isPathWithin(pathname, prefix))) return "POLICY";

  if ([
    "/admin/super",
    "/admin/permission-requests",
    "/admin/permissions",
    "/admin/permission-groups",
  ].some((prefix) => isPathWithin(pathname, prefix))) return "ADMIN_PERMISSION";

  if ([
    "/admin/users",
    "/admin/profiles",
    "/admin/consents",
    "/admin/application-cases",
    "/admin/company/applications",
  ].some((prefix) => isPathWithin(pathname, prefix))) return "USER";

  if (isPathWithin(pathname, "/admin/security")) return "SECURITY";

  if ([
    "/admin/payments",
    "/admin/plans",
    "/admin/credits",
    "/admin/rewards",
    "/admin/refunds",
    "/admin/refund-policies",
    "/admin/billing",
  ].some((prefix) => isPathWithin(pathname, prefix))) return "BILLING";

  if ([
    "/admin/pending-counts",
    "/admin/ads",
    "/admin/company/job-postings",
    "/admin/community",
    "/admin/notices",
    "/admin/faq",
    "/admin/guidelines",
    "/admin/tickets",
    "/admin/inquiries",
    "/admin/notifications",
    "/admin/collaboration",
  ].some((prefix) => isPathWithin(pathname, prefix))) return "CONTENT";

  if ([
    "/admin/analytics",
    "/admin/fit-analysis",
    "/admin/fit-analyses",
    "/admin/ai-usage",
    "/admin/ai-settings",
    "/admin/job-analysis",
    "/admin/company-analysis",
    "/admin/interview",
    "/admin/interviews",
    "/admin/corrections",
    "/admin/prompts",
    "/admin/chatbot",
    "/admin/ai",
  ].some((prefix) => isPathWithin(pathname, prefix))) return "AI";

  return null;
}

type AdminMockMutationMethod = "POST" | "PUT" | "PATCH" | "DELETE";

interface AdminMockMutationPolicy {
  method: AdminMockMutationMethod;
  pattern: RegExp;
  permission: AdminPermissionCode | "ROLE_ONLY";
}

type AdminMockPermissionRequirement =
  | AdminPermissionCode
  | readonly AdminPermissionCode[]
  | "ROLE_ONLY";

/**
 * POST를 일괄 CREATE로 추정하면 approve, issue, import 같은 명령형 API가 잘못 열린다.
 * 장애 데모 mock에서 사용하는 변경 API도 실제 endpoint 의미와 같은 exact 권한을 요구한다.
 * 여기에 없는 변경 API는 아래 canAccessMockApi에서 fail-closed 처리한다.
 */
const ADMIN_MOCK_MUTATION_POLICIES: readonly AdminMockMutationPolicy[] = [
  // 내 관리자 알림 설정
  { method: "PATCH", pattern: /^\/admin\/me\/notification-categories$/, permission: "ROLE_ONLY" },

  // 회원·지원 건·기업 신청
  { method: "POST", pattern: /^\/admin\/users$/, permission: "USER_CREATE" },
  { method: "POST", pattern: /^\/admin\/users\/bulk\/status$/, permission: "USER_UPDATE" },
  { method: "PATCH", pattern: /^\/admin\/users\/[^/]+\/status$/, permission: "USER_UPDATE" },
  { method: "POST", pattern: /^\/admin\/users\/bulk-delete$/, permission: "USER_DELETE" },
  { method: "DELETE", pattern: /^\/admin\/users\/[^/]+$/, permission: "USER_DELETE" },
  { method: "PATCH", pattern: /^\/admin\/application-cases\/[^/]+\/status$/, permission: "USER_UPDATE" },
  { method: "POST", pattern: /^\/admin\/company\/applications\/[^/]+\/(?:approve|reject)$/, permission: "USER_UPDATE" },

  // 보안 운영
  { method: "PATCH", pattern: /^\/admin\/security\/login-risk-policy$/, permission: "SECURITY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/security\/block-rules$/, permission: "SECURITY_CREATE" },
  { method: "PATCH", pattern: /^\/admin\/security\/block-rules\/[^/]+$/, permission: "SECURITY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/security\/block-rules\/[^/]+\/waf-sync$/, permission: "SECURITY_UPDATE" },
  { method: "PATCH", pattern: /^\/admin\/security\/providers\/[^/]+$/, permission: "SECURITY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/security\/providers\/[^/]+\/health-check$/, permission: "SECURITY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/security\/reviews$/, permission: "SECURITY_CREATE" },
  { method: "PATCH", pattern: /^\/admin\/security\/reviews\/[^/]+$/, permission: "SECURITY_UPDATE" },
  { method: "PATCH", pattern: /^\/admin\/security\/appeal-policy$/, permission: "SECURITY_UPDATE" },
  { method: "PATCH", pattern: /^\/admin\/security\/appeals\/[^/]+$/, permission: "SECURITY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/security\/block-cache\/sync$/, permission: "SECURITY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/security\/block-batches$/, permission: "SECURITY_CREATE" },
  { method: "POST", pattern: /^\/admin\/security\/block-batches\/[^/]+\/toggle$/, permission: "SECURITY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/security\/policy-feed\/(?:upload|import)$/, permission: "SECURITY_CREATE" },
  { method: "POST", pattern: /^\/admin\/security\/waf-sync\/process$/, permission: "SECURITY_UPDATE" },

  // 결제·요금제·리워드
  { method: "POST", pattern: /^\/admin\/refunds\/[^/]+\/(?:approve|reject)$/, permission: "BILLING_UPDATE" },
  { method: "POST", pattern: /^\/admin\/plans\/policy-changes$/, permission: "BILLING_CREATE" },
  { method: "POST", pattern: /^\/admin\/plans\/policy-changes\/[^/]+\/cancel$/, permission: "BILLING_UPDATE" },
  { method: "PUT", pattern: /^\/admin\/refund-policies\/draft$/, permission: "BILLING_UPDATE" },
  { method: "POST", pattern: /^\/admin\/refund-policies\/[^/]+\/publish$/, permission: "BILLING_UPDATE" },
  { method: "POST", pattern: /^\/admin\/credits\/adjust$/, permission: "BILLING_UPDATE" },
  { method: "PUT", pattern: /^\/admin\/rewards\/rules\/[^/]+$/, permission: "BILLING_UPDATE" },
  { method: "PATCH", pattern: /^\/admin\/rewards\/rules\/[^/]+\/enabled$/, permission: "BILLING_UPDATE" },
  { method: "POST", pattern: /^\/admin\/rewards\/levels$/, permission: "BILLING_CREATE" },
  { method: "PUT", pattern: /^\/admin\/rewards\/levels\/[^/]+$/, permission: "BILLING_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/rewards\/levels\/[^/]+$/, permission: "BILLING_DELETE" },
  { method: "POST", pattern: /^\/admin\/rewards\/coupons$/, permission: "BILLING_CREATE" },
  { method: "PUT", pattern: /^\/admin\/rewards\/coupons\/[^/]+$/, permission: "BILLING_UPDATE" },
  { method: "POST", pattern: /^\/admin\/rewards\/coupons\/[^/]+\/issue$/, permission: "BILLING_CREATE" },

  // 콘텐츠 운영
  { method: "POST", pattern: /^\/admin\/ads$/, permission: "CONTENT_CREATE" },
  { method: "PUT", pattern: /^\/admin\/ads\/[^/]+$/, permission: "CONTENT_UPDATE" },
  { method: "POST", pattern: /^\/admin\/ads\/[^/]+\/toggle-active$/, permission: "CONTENT_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/ads\/[^/]+$/, permission: "CONTENT_DELETE" },
  { method: "POST", pattern: /^\/admin\/company\/job-postings\/[^/]+\/(?:approve|reject)$/, permission: "CONTENT_UPDATE" },
  { method: "PATCH", pattern: /^\/admin\/community\/posts\/[^/]+\/status$/, permission: "CONTENT_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/community\/posts\/[^/]+$/, permission: "CONTENT_DELETE" },
  { method: "POST", pattern: /^\/admin\/community\/reports\/[^/]+\/reactivate$/, permission: "CONTENT_UPDATE" },
  { method: "POST", pattern: /^\/admin\/community\/reports\/[^/]+\/reclassify$/, permission: "AI_CREATE" },
  { method: "POST", pattern: /^\/admin\/notices$/, permission: "CONTENT_CREATE" },
  { method: "PUT", pattern: /^\/admin\/notices\/[^/]+$/, permission: "CONTENT_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/notices\/[^/]+$/, permission: "CONTENT_DELETE" },
  { method: "POST", pattern: /^\/admin\/faq$/, permission: "CONTENT_CREATE" },
  { method: "PUT", pattern: /^\/admin\/faq\/[^/]+$/, permission: "CONTENT_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/faq\/[^/]+$/, permission: "CONTENT_DELETE" },
  { method: "POST", pattern: /^\/admin\/faq\/embed-all$/, permission: "CONTENT_UPDATE" },
  { method: "POST", pattern: /^\/admin\/guidelines$/, permission: "CONTENT_CREATE" },
  { method: "PUT", pattern: /^\/admin\/guidelines\/[^/]+$/, permission: "CONTENT_UPDATE" },
  { method: "POST", pattern: /^\/admin\/guidelines\/[^/]+\/publish$/, permission: "CONTENT_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/guidelines\/[^/]+$/, permission: "CONTENT_DELETE" },
  { method: "PATCH", pattern: /^\/admin\/tickets\/[^/]+$/, permission: "CONTENT_UPDATE" },
  { method: "POST", pattern: /^\/admin\/tickets\/[^/]+\/reply$/, permission: "CONTENT_CREATE" },
  { method: "POST", pattern: /^\/admin\/notifications$/, permission: "CONTENT_CREATE" },
  { method: "PATCH", pattern: /^\/admin\/ai\/moderation\/review-queue\/[^/]+$/, permission: "CONTENT_UPDATE" },
  { method: "POST", pattern: /^\/admin\/ai\/moderation\/(?:comments\/)?[^/]+\/restore$/, permission: "CONTENT_UPDATE" },
  { method: "POST", pattern: /^\/admin\/ai\/moderation\/(?:comments\/)?[^/]+\/delete$/, permission: "CONTENT_DELETE" },

  // AI 운영·운영 메모
  { method: "PATCH", pattern: /^\/admin\/ai-settings\/(?:job-posting-fallback|upload-size)$/, permission: "AI_UPDATE" },
  { method: "POST", pattern: /^\/admin\/ai\/moderation-test$/, permission: "AI_CREATE" },
  { method: "PATCH", pattern: /^\/admin\/ai\/moderation\/settings$/, permission: "AI_UPDATE" },
  { method: "PATCH", pattern: /^\/admin\/analytics\/quality-flags\/[^/]+\/[^/]+\/resolve$/, permission: "AI_UPDATE" },
  { method: "POST", pattern: /^\/admin\/analytics\/runs\/[^/]+\/memos$/, permission: "AI_CREATE" },
  { method: "PATCH", pattern: /^\/admin\/analytics\/runs\/[^/]+\/memos\/[^/]+$/, permission: "AI_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/analytics\/runs\/[^/]+\/memos\/[^/]+$/, permission: "AI_DELETE" },
  { method: "PATCH", pattern: /^\/admin\/fit-analyses\/[^/]+\/gate-review$/, permission: "AI_UPDATE" },
  { method: "POST", pattern: /^\/admin\/fit-analyses\/[^/]+\/memos$/, permission: "AI_CREATE" },
  { method: "PATCH", pattern: /^\/admin\/fit-analyses\/[^/]+\/memos\/[^/]+$/, permission: "AI_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/fit-analyses\/[^/]+\/memos\/[^/]+$/, permission: "AI_DELETE" },
  { method: "PATCH", pattern: /^\/admin\/job-analysis\/[^/]+\/memo$/, permission: "AI_UPDATE" },
  { method: "PATCH", pattern: /^\/admin\/company-analysis\/[^/]+\/(?:memo|metadata)$/, permission: "AI_UPDATE" },
  { method: "PUT", pattern: /^\/admin\/corrections\/[^/]+\/memo$/, permission: "AI_UPDATE" },
  { method: "PUT", pattern: /^\/admin\/interview\/sessions\/[^/]+\/memo$/, permission: "AI_UPDATE" },
  { method: "POST", pattern: /^\/admin\/interview\/knowledge$/, permission: "AI_CREATE" },
  { method: "PUT", pattern: /^\/admin\/interview\/knowledge\/[^/]+$/, permission: "AI_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/interview\/knowledge\/[^/]+$/, permission: "AI_DELETE" },
  { method: "POST", pattern: /^\/admin\/interview\/knowledge\/reindex$/, permission: "AI_UPDATE" },
  { method: "POST", pattern: /^\/admin\/interview\/training\/(?:eval|fine-tune)$/, permission: "AI_CREATE" },
  { method: "PATCH", pattern: /^\/admin\/chatbot\/quota-policy$/, permission: "AI_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/chatbot\/conversations\/[^/]+$/, permission: "AI_DELETE" },
  { method: "PATCH", pattern: /^\/admin\/chatbot\/unanswered\/[^/]+\/status$/, permission: "AI_UPDATE" },
  { method: "POST", pattern: /^\/admin\/chatbot\/unanswered\/[^/]+\/draft$/, permission: "AI_CREATE" },
  { method: "POST", pattern: /^\/admin\/chatbot\/unanswered\/[^/]+\/convert$/, permission: "CONTENT_CREATE" },
  { method: "POST", pattern: /^\/admin\/tickets\/[^/]+\/(?:draft|member-summary)$/, permission: "AI_CREATE" },

  // 정책·런타임 설정·직원 등급·법적 문서
  { method: "PATCH", pattern: /^\/admin\/policies\/[^/]+$/, permission: "POLICY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/policies\/[^/]+\/run$/, permission: "POLICY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/runtime-settings$/, permission: "POLICY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/settings\/import$/, permission: "POLICY_UPDATE" },
  { method: "PUT", pattern: /^\/admin\/staff-grades\/[^/]+$/, permission: "POLICY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/staff-grades\/import\/preview$/, permission: "POLICY_READ" },
  { method: "POST", pattern: /^\/admin\/staff-grades\/import\/apply$/, permission: "POLICY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/legal\/[^/]+\/versions$/, permission: "POLICY_CREATE" },
  { method: "PUT", pattern: /^\/admin\/legal\/versions\/[^/]+$/, permission: "POLICY_UPDATE" },
  { method: "POST", pattern: /^\/admin\/legal\/versions\/[^/]+\/publish$/, permission: "POLICY_UPDATE" },
  { method: "DELETE", pattern: /^\/admin\/legal\/versions\/[^/]+$/, permission: "POLICY_DELETE" },
  { method: "PUT", pattern: /^\/admin\/mfa-policy$/, permission: "POLICY_UPDATE" },
];

function adminMockReportActionPermissions(body: unknown): readonly AdminPermissionCode[] | null {
  if (!body || typeof body !== "object" || !("action" in body)) return null;
  const rawAction = (body as { action?: unknown }).action;
  if (typeof rawAction !== "string") return null;
  const action = rawAction.trim().toUpperCase();
  if (action === "DELETED") return ["CONTENT_DELETE"];
  if (action === "DELETE_AND_BLOCK") return ["CONTENT_DELETE", "USER_UPDATE"];
  if (action === "BLOCK_AUTHOR") return ["CONTENT_UPDATE", "USER_UPDATE"];
  if (["HIDDEN", "DISMISSED", "RESTORE", "PUBLISHED"].includes(action)) {
    return ["CONTENT_UPDATE"];
  }
  return null;
}

function adminMockMutationPermission(
  pathname: string,
  method: string,
  body?: unknown,
): AdminMockPermissionRequirement | null {
  const normalizedMethod = method.toUpperCase();
  if (!["POST", "PUT", "PATCH", "DELETE"].includes(normalizedMethod)) return null;
  if (normalizedMethod === "POST"
    && /^\/admin\/community\/reports\/[^/]+\/action$/.test(pathname)) {
    return adminMockReportActionPermissions(body);
  }
  return ADMIN_MOCK_MUTATION_POLICIES.find((policy) => (
    policy.method === normalizedMethod && policy.pattern.test(pathname)
  ))?.permission ?? null;
}

/**
 * mock API도 화면 guard와 별개로 `/admin/**` 요청을 exact permission으로 다시 검사한다.
 * 매핑되지 않은 관리자 API는 허용 추정하지 않고 닫아 새 엔드포인트가 무권한으로 노출되지 않게 한다.
 */
export function canAccessMockApi(
  path: string,
  role: string | null | undefined,
  grantedPermissions: ReadonlySet<string>,
  method = "GET",
  body?: unknown,
): boolean {
  const pathname = path.split("?", 1)[0];
  if (pathname !== "/admin" && !pathname.startsWith("/admin/")) return true;
  if (!isAdminRole(role)) return false;
  if (role === "SUPER_ADMIN") return true;
  if (isPathWithin(pathname, "/admin/super")) return false;
  if ([
    "/admin/runtime-settings",
    "/admin/settings",
    "/admin/staff-grades",
    "/admin/security/providers",
  ].some((prefix) => isPathWithin(pathname, prefix))) return false;

  const normalizedMethod = method.toUpperCase();
  const isReadRequest = normalizedMethod === "GET" || normalizedMethod === "HEAD" || normalizedMethod === "OPTIONS";
  if (!isReadRequest) {
    const required = adminMockMutationPermission(pathname, normalizedMethod, body);
    if (!required) return false;
    if (required === "ROLE_ONLY") return true;
    const requiredPermissions = Array.isArray(required) ? required : [required];
    return requiredPermissions.every((permission) => (
      hasAnyAdminPermission(role, grantedPermissions, [permission])
    ));
  }

  const domain = adminMockDomain(pathname);
  if (domain === "ME") return true;
  if (domain === "DASHBOARD") {
    return hasAnyAdminPermission(role, grantedPermissions, ["USER_READ", "AI_READ"]);
  }
  if (domain === "AUDIT") {
    return hasAnyAdminPermission(role, grantedPermissions, ["AUDIT_READ"]);
  }
  if (!domain) return false;

  const required = adminPermissionCode(domain, "READ");
  return hasAnyAdminPermission(role, grantedPermissions, [required]);
}
