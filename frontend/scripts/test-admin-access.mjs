import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { transformWithEsbuild } from "vite";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const read = (path) => readFileSync(resolve(root, path), "utf8");

const accessSource = read("src/admin/auth/adminAccess.ts");
const boundarySource = read("src/admin/auth/AdminRouteBoundary.tsx");
const routesSource = read("src/admin/routes.ts");
const shellSource = read("src/admin/components/AdminShell.tsx");
const shellCssSource = read("src/admin/components/admin-shell.css");
const authorizationSource = read("src/admin/auth/useAdminAuthorization.ts");
const mockSource = read("src/app/lib/mock/index.ts");
const mockPermissionSource = read("src/app/lib/mock/domains/admin/permission.ts");
const mockUsersSource = read("src/app/lib/mock/domains/admin/users.ts");
const mockCoreSource = read("src/app/lib/mock/domains/admin/core.ts");
const userApiSource = read("src/admin/features/users/api.ts");
const usersPageSource = read("src/admin/features/users/pages/AdminUsersPage.tsx");
const userDetailSource = read("src/admin/features/users/components/UserDetailPanel.tsx");
const adsSource = read("src/admin/features/ads/pages/AdminAdsPage.tsx");
const noticesSource = read("src/admin/features/notices/pages/AdminNotices.tsx");
const noticeComposeSource = read("src/admin/features/notices/pages/NoticeCompose.tsx");
const noticeApiSource = read("src/admin/features/notices/api/adminNoticeApi.ts");
const faqSource = read("src/admin/features/faqs/pages/AdminFaq.tsx");
const faqComposeSource = read("src/admin/features/faqs/pages/FaqCompose.tsx");
const plansSource = read("src/admin/features/billing/pages/AdminPlansPage.tsx");
const refundPolicySource = read("src/admin/features/billing/components/RefundPolicySection.tsx");
const rewardsSource = read("src/admin/features/reward/pages/AdminRewardsPage.tsx");
const aiSettingsSource = read("src/admin/features/settings/pages/AdminAiSettingsPage.tsx");
const securityOpsSource = read("src/admin/features/security-ops/pages/AdminSecurityOpsPage.tsx");
const blockEngineSource = read("src/admin/features/security-ops/components/BlockEnginePanel.tsx");
const reportsSource = read("src/admin/features/community/pages/AdminReports.tsx");
const superAdminSource = read("src/admin/features/super-admin/pages/AdminSuperAdminPage.tsx");
const aiSupportSource = read("src/admin/features/ai-support/pages/AdminAiSupport.tsx");
const analyticsSource = read("src/admin/features/analytics/pages/AdminAnalyticsPage.tsx");
const applicationCasesSource = read("src/admin/features/application-cases/pages/AdminApplicationCasesPage.tsx");
const chatbotGovernanceSource = read("src/admin/features/chatbot-governance/pages/AdminChatbotGovernancePage.tsx");
const guidelinesSource = read("src/admin/features/community/pages/AdminGuidelines.tsx");
const companyAnalysisSource = read("src/admin/features/company-analysis/pages/AdminCompanyAnalysisPage.tsx");
const correctionsSource = read("src/admin/features/corrections/pages/AdminCorrectionsPage.tsx");
const correctionDialogSource = read("src/admin/features/corrections/components/CorrectionDetailDialog.tsx");
const fitAnalysisSource = read("src/admin/features/fit-analysis/pages/AdminFitAnalysis.tsx");
const interviewReportDialogSource = read("src/admin/features/interview-reports/components/ReportDetailDialog.tsx");
const interviewsSource = read("src/admin/features/interviews/pages/AdminInterviewsPage.tsx");
const trainingPipelineSource = read("src/admin/features/interviews/components/TrainingPipelineCard.tsx");
const jobAnalysisSource = read("src/admin/features/job-analysis/pages/AdminJobAnalysisPage.tsx");
const inquiriesSource = read("src/admin/features/support-tickets/pages/AdminInquiries.tsx");
const inquiriesAiSource = read("src/admin/features/support-tickets/pages/AdminInquiriesAI.tsx");
const apiSource = read("src/app/lib/api.ts");
const authSource = read("src/app/auth/AuthContext.tsx");
const tokenStoreSource = read("src/app/lib/tokenStore.ts");
const mainSource = read("src/main.tsx");
const serviceWorkerUpdateSource = read("src/app/lib/serviceWorkerUpdate.ts");
const viteConfigSource = read("vite.config.ts");
const serviceWorkerUpdateJavaScript = await transformWithEsbuild(
  serviceWorkerUpdateSource,
  "serviceWorkerUpdate.ts",
  { loader: "ts", format: "esm", target: "es2022" },
);
const serviceWorkerUpdateModule = await import(
  `data:text/javascript;base64,${Buffer.from(serviceWorkerUpdateJavaScript.code).toString("base64")}`
);

function sorted(values) {
  return [...values].sort();
}

test("55개 관리자 route와 중앙 정책표가 정확히 일치한다", () => {
  const policyBlock = accessSource.match(/ADMIN_ROUTE_POLICIES\s*=\s*\{([\s\S]*?)\n\} as const/);
  assert.ok(policyBlock, "ADMIN_ROUTE_POLICIES block");
  const policyPaths = [...policyBlock[1].matchAll(/^\s{2}(?:admin|"([^"]+)"):\s*\{/gm)]
    .map((match) => match[1] ?? "admin");
  const routePaths = [...routesSource.matchAll(/adminRoute\("([^"]+)"/g)].map((match) => match[1]);
  assert.equal(policyPaths.length, 55);
  assert.equal(routePaths.length, 55);
  assert.deepEqual(sorted(routePaths), sorted(policyPaths));
});

test("민감 설정·급여 경로는 SUPER_ADMIN 전용이고 일반 정책 화면만 위임된다", () => {
  const superOnly = [...accessSource.matchAll(/^\s{2}(?:"([^"]+)"|admin):\s*\{[^\n]*superOnly:\s*true[^\n]*\}/gm)]
    .map((match) => match[1] ?? "admin");
  assert.deepEqual(superOnly, ["admin/super", "admin/runtime-settings", "admin/staff-grades"]);
  assert.match(accessSource, /"admin\/policies": \{ permissionCodes: \["POLICY_READ"\] \}/);
  assert.match(accessSource, /"admin\/runtime-settings": \{ permissionCodes: \["POLICY_READ"\], superOnly: true \}/);
  assert.match(accessSource, /"admin\/staff-grades": \{ permissionCodes: \["POLICY_READ"\], superOnly: true \}/);
  assert.match(routesSource, /handle:\s*\{\s*adminAccess\s*\}/);
  assert.match(routesSource, /render:\s*\(\)\s*=>\s*createElement\(Component\)/);
  assert.match(boundarySource, /if \(decision === "forbidden"\)/);
  assert.match(boundarySource, /return <\>\{render\(\)\}<\/>;/);
});

test("익명·USER·권한 조회 실패는 fail-closed이고 로그인 복귀 경로를 보존한다", () => {
  assert.match(accessSource, /if \(!hasUser\) return "anonymous";/);
  assert.match(accessSource, /if \(!isAdminRole\(role\)\) return "forbidden";/);
  assert.match(accessSource, /if \(permissionStatus !== "ready"\) return "forbidden";/);
  assert.match(accessSource, /encodeURIComponent\(`\$\{pathname\}\$\{search\}`\)/);
  assert.match(boundarySource, /adminLoginRedirect\(location\.pathname, location\.search\)/);
});

test("canonical 29개 권한만 사용하고 라우트는 exact READ 권한으로 판정한다", () => {
  const permissionBlock = accessSource.match(/ADMIN_PERMISSION_CODES\s*=\s*\[([\s\S]*?)\]\s*as const/);
  assert.ok(permissionBlock, "ADMIN_PERMISSION_CODES block");
  const actual = [...permissionBlock[1].matchAll(/"([A-Z_]+)"/g)].map((match) => match[1]);
  const domains = ["USER", "SECURITY", "BILLING", "CONTENT", "AI", "POLICY", "ADMIN_PERMISSION"];
  const actions = ["READ", "CREATE", "UPDATE", "DELETE"];
  const expected = domains.flatMap((domain) => actions.map((action) => `${domain}_${action}`)).concat("AUDIT_READ");
  assert.deepEqual(actual, expected);
  assert.doesNotMatch(accessSource, /MEMBER_ADMIN|CONTENT_MANAGE|USER_STATUS_WRITE|permissionGroupsFromCodes/);
  assert.match(accessSource, /"admin\/users": \{ permissionCodes: \["USER_READ"\] \}/);
  assert.match(accessSource, /"admin\/application-cases": \{ permissionCodes: \["USER_READ"\] \}/);
  assert.match(accessSource, /"admin\/chatbot-governance": \{ permissionCodes: \["AI_READ"\] \}/);
  assert.match(accessSource, /"admin\/ai-support": \{ permissionCodes: \["AI_READ"\] \}/);
  assert.match(accessSource, /"admin\/terms": \{ permissionCodes: \["POLICY_READ"\] \}/);
  assert.match(accessSource, /"admin\/notices\/new": \{ permissionCodes: \["CONTENT_CREATE"\] \}/);
  assert.match(accessSource, /"admin\/faq\/new": \{ permissionCodes: \["CONTENT_CREATE"\] \}/);
  assert.match(accessSource, /"admin\/audit\/email": \{ permissionCodes: \["AUDIT_READ"\] \}/);
  assert.match(authorizationSource, /hasAnyAdminPermission\(role, grantedPermissions, requiredPermissions\)/);
});

test("mock /admin API와 outage 관리자 persona를 중앙 차단한다", () => {
  assert.match(mockSource, /export function canResolveMockRequest/);
  assert.match(mockSource, /if \(!canResolveMockRequest\(rawPath, method, body\)\) return MOCK_FORBIDDEN;/);
  assert.equal((apiSource.match(/value === mock\.MOCK_FORBIDDEN/g) ?? []).length, 2);
  assert.match(mockSource, /getOutageFallbackSnapshot\(\)\.mode !== "static-demo"/);
  assert.match(mockSource, /email === demoAdminUser\.email/);
  assert.match(mockSource, /new Set<string>\(session\.permissions\)/);
  assert.match(accessSource, /const ADMIN_MOCK_MUTATION_POLICIES: readonly AdminMockMutationPolicy\[\]/);
  assert.match(accessSource, /function adminMockReportActionPermissions/);
  assert.match(accessSource, /function adminMockModerationReviewPermissions/);
  assert.match(accessSource, /"DELETE_AND_BLOCK"\) return \["CONTENT_DELETE", "USER_UPDATE"\]/);
  assert.match(accessSource, /"BLOCK_AUTHOR"\) return \["CONTENT_UPDATE", "USER_UPDATE"\]/);
  assert.match(accessSource, /action === "KEEP"\) return \["AI_UPDATE"\]/);
  assert.match(accessSource, /action === "HIDE"\) return \["AI_UPDATE", "CONTENT_UPDATE"\]/);
  assert.match(accessSource, /requiredPermissions\.every/);
  assert.match(accessSource, /if \(!required\) return false;/);
  assert.match(accessSource, /if \(!domain\) return false;/);
  assert.match(accessSource, /if \(isPathWithin\(pathname, "\/admin\/super"\)\) return false;/);
  assert.match(accessSource, /"\/admin\/runtime-settings"[\s\S]*"\/admin\/settings"[\s\S]*"\/admin\/staff-grades"[\s\S]*\.some\(\(prefix\) => isPathWithin\(pathname, prefix\)\)\) return false;/);
  for (const policy of [
    '{ method: "POST", pattern: /^\\/admin\\/settings\\/import$/, permission: "POLICY_UPDATE" }',
    '{ method: "POST", pattern: /^\\/admin\\/staff-grades\\/import\\/apply$/, permission: "POLICY_UPDATE" }',
    '{ method: "POST", pattern: /^\\/admin\\/rewards\\/coupons\\/[^/]+\\/issue$/, permission: "BILLING_CREATE" }',
    '{ method: "PATCH", pattern: /^\\/admin\\/community\\/posts\\/[^/]+\\/status$/, permission: "CONTENT_UPDATE" }',
    '{ method: "DELETE", pattern: /^\\/admin\\/community\\/posts\\/[^/]+$/, permission: "CONTENT_DELETE" }',
    '{ method: "POST", pattern: /^\\/admin\\/community\\/reports\\/[^/]+\\/reactivate$/, permission: "CONTENT_UPDATE" }',
    '{ method: "POST", pattern: /^\\/admin\\/community\\/reports\\/[^/]+\\/reclassify$/, permission: "AI_CREATE" }',
  ]) {
    assert.ok(accessSource.includes(policy), `explicit mock mutation policy: ${policy}`);
  }
  assert.match(mockPermissionSource, /ADMIN_PERMISSION_CODES\.filter/);
  assert.doesNotMatch(mockPermissionSource, /MEMBER_ADMIN|CONTENT_MANAGE|AUDIT_ADMIN/);
  assert.match(mockCoreSource, /pattern: \/\^\\\/admin\\\/email-audit\$\//);
  assert.match(mockCoreSource, /pattern: \/\^\\\/admin\\\/audit\\\/logins\$\//);
});

test("token 변경은 role을 /auth/me로 재검증하고 AdminShell은 실제 사용자를 표시한다", () => {
  assert.match(authSource, /if \(event === "cleared"\)[\s\S]*void refreshMe\(\);/);
  assert.match(authSource, /const generation = \+\+refreshGeneration\.current;/);
  assert.match(shellSource, /\{displayName\} · \{displayRole\}/);
  assert.doesNotMatch(shellSource, /<span className="adm__profile-name">관리자<\/span>/);
  assert.match(shellSource, /useAdminPendingCounts\(canUseAdmin && canReadPending\)/);
  assert.match(shellSource, /if \(!canUseAdmin\)/);
});

test("관리자 메뉴는 7개 그룹으로 정리되고 검색·아코디언·모바일 드로어가 실제 동작한다", () => {
  const groups = [...shellSource.matchAll(/^    key: "(overview|member|security|ai|billing|content|policy)",$/gm)].map((match) => match[1]);
  assert.deepEqual(groups, ["overview", "member", "security", "ai", "billing", "content", "policy"]);
  assert.match(shellSource, /const \[navQuery, setNavQuery\] = useState\(""\)/);
  assert.match(shellSource, /item\.label\.toLocaleLowerCase\("ko-KR"\)\.includes\(normalizedNavQuery\)/);
  assert.match(shellSource, /aria-label="관리자 메뉴 검색"/);
  assert.match(shellSource, /aria-expanded=\{isExpanded\}/);
  assert.match(shellSource, /ref=\{mobileNavSearchRef\}/);
  assert.match(shellSource, /role=\{mobileNavOpen \? "dialog" : undefined\}/);
  assert.match(shellSource, /window\.requestAnimationFrame\(\(\) => mobileNavSearchRef\.current\?\.focus\(\)\)/);
  assert.match(shellSource, /event\.key !== "Tab"/);
  assert.match(shellSource, /disabled=\{normalizedNavQuery\.length > 0\}/);
  assert.doesNotMatch(shellSource, /matchMedia/);
  assert.match(shellCssSource, /@media \(max-width: 980px\)[\s\S]*\.adm__side-search \{[\s\S]*display: flex/);
  assert.match(shellCssSource, /@media \(max-width: 980px\)[\s\S]*\.adm__search \{ display: none; \}/);
});

test("직접 공지·FAQ 작성 화면도 CREATE 권한과 실제 저장 필드만 사용한다", () => {
  for (const [name, source] of [["notice", noticeComposeSource], ["faq", faqComposeSource]]) {
    assert.match(source, /useAdminDomainAuthorization\("CONTENT"\)/, `${name} compose authorization`);
    assert.match(source, /if \(!canCreate \|\| !canSubmit \|\| saving\) return;/, `${name} submit guard`);
    assert.match(source, /\{canCreate && \(/, `${name} submit visibility`);
    assert.doesNotMatch(source, /미리보기|임시 저장|임시저장됨|onClick=\{\(\) => \{\}\}/, `${name} fake controls`);
  }
  assert.match(noticeComposeSource, /category: cat/);
  assert.match(noticeComposeSource, /scheduledAt: when === "예약" \? scheduleAt : null/);
  assert.equal((noticeApiSource.match(/scheduledAt\?: string \| null/g) ?? []).length, 2);
  assert.match(faqComposeSource, /sanitizePostHtml\(a\)/);
});

test("회원 생성·수정·삭제 UI와 API가 서로 다른 exact 권한을 사용한다", () => {
  assert.match(usersPageSource, /authorization\.can\("USER_CREATE"\)/);
  assert.match(usersPageSource, /authorization\.can\("USER_UPDATE"\)/);
  assert.match(usersPageSource, /authorization\.can\("USER_DELETE"\)/);
  assert.match(userApiSource, /createAdminUser[\s\S]*method: "POST"/);
  assert.match(userApiSource, /softDeleteAdminUser[\s\S]*method: "DELETE"/);
  assert.match(userApiSource, /bulkSoftDeleteAdminUsers[\s\S]*\/admin\/users\/bulk-delete/);
  assert.match(userDetailSource, /authorization\.can\("USER_UPDATE"\)/);
  assert.match(userDetailSource, /authorization\.can\("USER_DELETE"\)/);
  assert.match(userDetailSource, /filter\(\(\w+\) => \w+\.value !== "DELETED"\)/);
  assert.match(mockUsersSource, /method: "POST",\s*pattern: \/\^\\\/admin\\\/users\$\//);
  assert.match(mockUsersSource, /pattern: \/\^\\\/admin\\\/users\\\/bulk-delete\$\//);
});

test("주요 관리자 mutation 화면은 도메인 CRUD 권한으로 버튼과 핸들러를 함께 닫는다", () => {
  for (const [name, source, domain] of [
    ["ads", adsSource, "CONTENT"],
    ["notices", noticesSource, "CONTENT"],
    ["faq", faqSource, "CONTENT"],
    ["plans", plansSource, "BILLING"],
    ["refund policy", refundPolicySource, "BILLING"],
    ["AI settings", aiSettingsSource, "AI"],
    ["security ops", securityOpsSource, "SECURITY"],
    ["block engine", blockEngineSource, "SECURITY"],
  ]) {
    assert.match(source, new RegExp(`useAdminDomainAuthorization\\("${domain}"\\)`), `${name} domain authorization`);
  }
  assert.match(adsSource, /canCreate[\s\S]*canUpdate[\s\S]*canDelete/);
  assert.match(faqSource, /if \(!canCreate \|\| !canSubmit \|\| saving\) return;/);
  assert.match(plansSource, /if \(!canCreate \|\| !selectedTarget/);
  assert.match(refundPolicySource, /if \(!canUpdate\) return null;/);
  assert.match(rewardsSource, /const issue = async[\s\S]*if \(!canCreate\) return;/);
  assert.match(rewardsSource, /\{canCreate && <button[^>]*onClick=\{\(\) => void issue\(c\)\}>발급<\/button>\}/);
  assert.match(aiSettingsSource, /if \(!canUpdate\) return;/);
  assert.match(securityOpsSource, /if \(!canCreate\) return;/);
  assert.match(securityOpsSource, /if \(!canUpdate\) return;/);
  assert.match(reportsSource, /authorization\.can\("USER_UPDATE"\)/);
  assert.match(reportsSource, /isAuthorBlockAction\(dialog\.action\) && !canUpdateUsers/);
  assert.match(reportsSource, /canDelete && canUpdateUsers/);
});

test("조회 화면에 섞인 mutation도 백엔드 exact CRUD 권한으로 숨기고 fail-closed 처리한다", () => {
  for (const [name, source, domain] of [
    ["analytics", analyticsSource, "AI"],
    ["application cases", applicationCasesSource, "USER"],
    ["chatbot governance", chatbotGovernanceSource, "AI"],
    ["guidelines", guidelinesSource, "CONTENT"],
    ["company analysis", companyAnalysisSource, "AI"],
    ["corrections", correctionsSource, "AI"],
    ["fit analysis", fitAnalysisSource, "AI"],
    ["interview report", interviewReportDialogSource, "AI"],
    ["interviews", interviewsSource, "AI"],
    ["job analysis", jobAnalysisSource, "AI"],
    ["inquiries", inquiriesSource, "CONTENT"],
  ]) {
    assert.match(source, new RegExp(`useAdminDomainAuthorization\\("${domain}"\\)`), `${name} domain authorization`);
  }

  assert.match(aiSupportSource, /useAdminDomainAuthorization\("AI"\)/);
  assert.match(aiSupportSource, /useAdminDomainAuthorization\("CONTENT"\)/);
  assert.match(aiSupportSource, /if \(!aiAuthorization\.canCreate \|\| !cluster\) return;/);
  assert.match(aiSupportSource, /if \(!contentAuthorization\.canCreate \|\| !cluster \|\| !draftEdit\) return;/);
  assert.match(aiSupportSource, /if \(!contentAuthorization\.canUpdate\) return;/);

  assert.match(analyticsSource, /if \(!canCreate \|\| !content\.trim\(\) \|\| saving\) return;/);
  assert.match(analyticsSource, /if \(!canUpdate \|\| !editContent\.trim\(\) \|\| saving\) return;/);
  assert.match(analyticsSource, /if \(!canDelete \|\| saving\) return;/);
  assert.match(applicationCasesSource, /if \(!canUpdate \|\| !selected\) return;/);
  assert.match(chatbotGovernanceSource, /if \(!canUpdate\) return;/);
  assert.match(chatbotGovernanceSource, /if \(!canDelete\) return;/);
  assert.match(guidelinesSource, /if \(saving \|\| \(editId \? !canUpdate : !canCreate\)\) return;/);
  assert.match(companyAnalysisSource, /const saveMemo[\s\S]*if \(!canUpdate\) return;/);
  assert.match(companyAnalysisSource, /const saveMetadata[\s\S]*if \(!canUpdate\) return;/);
  assert.match(correctionsSource, /if \(!canUpdate\) throw new Error\("운영 메모 수정 권한이 없습니다\."\);/);
  assert.match(correctionDialogSource, /if \(!canUpdate \|\| !detail \|\| saving\) return;/);
  assert.match(fitAnalysisSource, /if \(editingMemo \? !canUpdate : !canCreate\) return;/);
  assert.match(fitAnalysisSource, /if \(!canDelete \|\| !detail\) return;/);
  assert.match(interviewReportDialogSource, /if \(!canUpdate\) return;/);
  assert.match(interviewsSource, /<TrainingPipelineCard canCreate=\{canCreate\} \/>/);
  assert.match(trainingPipelineSource, /if \(!canCreate\) return;/);
  assert.match(jobAnalysisSource, /const saveMemo[\s\S]*if \(!canUpdate\) return;/);
  assert.match(inquiriesSource, /if \(!canUpdate \|\| !dialog\) return;/);
  assert.match(inquiriesAiSource, /useAdminDomainAuthorization\("AI"\)/);
  assert.match(inquiriesAiSource, /useAdminDomainAuthorization\("CONTENT"\)/);
  assert.match(inquiriesAiSource, /if \(!canCreateContent \|\| !selected \|\| !replyText\.trim\(\)\) return;/);
  assert.match(inquiriesAiSource, /if \(!canUpdateContent \|\| !selected \|\| status === selected\.status\) return;/);
});

test("보안 Provider 설정은 SUPER_ADMIN에게만 조회·노출·수정된다", () => {
  assert.match(securityOpsSource, /const canManageProviders = role === "SUPER_ADMIN";/);
  assert.match(securityOpsSource, /canManageProviders\s*\? securityApi\.getProviders\(\)/);
  assert.match(securityOpsSource, /\.\.\.\(canManageProviders \? \[\["providers", "Provider 설정"\]\] : \[\]\)/);
  assert.match(securityOpsSource, /tab === "providers" && canManageProviders/);
  assert.match(securityOpsSource, /if \(role !== "SUPER_ADMIN"\) return null;/);
});

test("super 권한 화면은 canonical 도메인·동작 매트릭스를 사용한다", () => {
  assert.match(superAdminSource, /ADMIN_PERMISSION_DOMAINS/);
  assert.match(superAdminSource, /ADMIN_PERMISSION_ACTIONS/);
  assert.match(superAdminSource, /adminPermissionCode\(domain, action\)/);
  assert.match(superAdminSource, /togglePermission\("AUDIT_READ"\)/);
  assert.match(superAdminSource, /const persistedRole = detail\?\.id === selectedId \? detail\.role : \(selected\?\.role \?\? "USER"\);/);
  assert.match(superAdminSource, /const roleDirty = role !== persistedRole;/);
  assert.match(superAdminSource, /if \(roleDirty \|\| persistedRole === "USER"/);
  assert.match(superAdminSource, /disabled=\{saving \|\| !roleDirty\}/);
  assert.match(superAdminSource, /역할 변경을 먼저 저장해야 권한과 권한 그룹을 편집할 수 있습니다/);
  assert.doesNotMatch(superAdminSource, /USER_STATUS_WRITE|CONTENT_MANAGE/);
});

test("이전 세션의 비동기 응답은 새 로그인 토큰이나 사용자를 덮어쓰지 않는다", () => {
  assert.match(tokenStoreSource, /let revision = 0;/);
  assert.match(tokenStoreSource, /export function setTokensIfUnchanged/);
  assert.match(tokenStoreSource, /export function clearTokensIfUnchanged/);
  assert.match(tokenStoreSource, /revision !== expected\.revision/);
  assert.match(tokenStoreSource, /writeTokens\(tokens, "refreshed"\)/);
  assert.match(apiSource, /interface RefreshAttempt/);
  assert.match(apiSource, /sameSnapshot\(refreshAttempt\.snapshot, snapshot\)/);
  assert.match(apiSource, /const updated = setTokensIfUnchanged\(snapshot,/);
  assert.match(apiSource, /clearTokensIfUnchanged\(snapshot\);/);
  assert.match(apiSource, /let sessionSnapshot = withAuth \? getTokenStoreSnapshot\(\) : null;/);
  assert.match(apiSource, /assertAuthenticatedRequestStillCurrent\(sessionSnapshot\);/);
  assert.match(apiSource, /"AUTH_SESSION_CHANGED"/);
  assert.match(authSource, /generation === refreshGeneration\.current[\s\S]*isTokenStoreSnapshotCurrent\(sessionSnapshot\)/);
  assert.match(authSource, /setUser\(null\);\s*clearTokensIfUnchanged\(sessionSnapshot\);/);
  assert.match(authSource, /if \(event === "refreshed"\)[\s\S]*void refreshMe\(\);/);
  assert.match(authSource, /setUser\(null\);\s*setLoading\(true\);\s*void refreshMe\(\);/);
});

function fakeServiceWorker(initialController) {
  let controller = initialController;
  const listeners = [];

  return {
    get controller() {
      return controller;
    },
    addEventListener(event, listener) {
      assert.equal(event, "controllerchange");
      listeners.push(listener);
    },
    changeController(nextController) {
      controller = nextController;
      listeners.forEach((listener) => listener());
    },
  };
}

function fakeEventTarget() {
  const listeners = new Map();
  return {
    addEventListener(event, listener) {
      listeners.set(event, [...(listeners.get(event) ?? []), listener]);
    },
    dispatch(event, payload) {
      for (const listener of listeners.get(event) ?? []) listener(payload);
    },
  };
}

function fakeStorage() {
  const values = new Map();
  return {
    getItem(key) {
      return values.get(key) ?? null;
    },
    setItem(key, value) {
      values.set(key, value);
    },
  };
}

function fakeHistory(initialState) {
  return {
    state: initialState,
    replaceState(nextState, unused) {
      assert.equal(unused, "");
      this.state = nextState;
    },
  };
}

function assertHistoryFallbackPreventsReloadLoop(storage) {
  const routerState = {
    idx: 7,
    key: "applications-learning",
    usr: { from: "/applications" },
  };
  const history = fakeHistory(routerState);
  const firstDocument = fakeEventTarget();
  let firstReloadCount = 0;
  serviceWorkerUpdateModule.installStaleChunkReload(
    firstDocument,
    storage,
    () => firstReloadCount++,
    () => 10_000,
    serviceWorkerUpdateModule.createHistoryStateAdapter(history),
  );
  firstDocument.dispatch("vite:preloadError", { preventDefault() {} });
  assert.equal(firstReloadCount, 1);

  const markerKey = serviceWorkerUpdateModule.STALE_CHUNK_RELOAD_HISTORY_STATE_KEY;
  const { [markerKey]: marker, ...preservedRouterState } = history.state;
  assert.equal(marker, "10000");
  assert.deepEqual(preservedRouterState, routerState, "기존 React Router history.state를 보존해야 한다");

  const reloadedDocument = fakeEventTarget();
  let immediateReloadCount = 0;
  serviceWorkerUpdateModule.installStaleChunkReload(
    reloadedDocument,
    storage,
    () => immediateReloadCount++,
    () => 10_001,
    serviceWorkerUpdateModule.createHistoryStateAdapter(history),
  );
  reloadedDocument.dispatch("vite:preloadError", { preventDefault() {} });
  assert.equal(immediateReloadCount, 0, "reload 뒤 즉시 같은 chunk가 실패해도 반복 reload하지 않아야 한다");

  const laterDocument = fakeEventTarget();
  let laterReloadCount = 0;
  serviceWorkerUpdateModule.installStaleChunkReload(
    laterDocument,
    storage,
    () => laterReloadCount++,
    () => 10_000 + serviceWorkerUpdateModule.STALE_CHUNK_RELOAD_COOLDOWN_MS,
    serviceWorkerUpdateModule.createHistoryStateAdapter(history),
  );
  laterDocument.dispatch("vite:preloadError", { preventDefault() {} });
  assert.equal(laterReloadCount, 1, "쿨다운 뒤의 새 배포 실패는 다시 복구해야 한다");
}

test("오래된 Vite lazy chunk는 최신 문서를 한 번 다시 받는다", () => {
  const eventTarget = fakeEventTarget();
  const storage = fakeStorage();
  let reloadCount = 0;
  let preventedCount = 0;
  const event = { preventDefault: () => preventedCount++ };

  serviceWorkerUpdateModule.installStaleChunkReload(
    eventTarget,
    storage,
    () => reloadCount++,
    () => 10_000,
  );
  eventTarget.dispatch("vite:preloadError", event);
  eventTarget.dispatch("vite:preloadError", event);

  assert.equal(reloadCount, 1);
  assert.equal(preventedCount, 2);
  assert.equal(
    storage.getItem(serviceWorkerUpdateModule.STALE_CHUNK_RELOAD_STORAGE_KEY),
    "10000",
  );
});

test("오래된 chunk 복구 직후 새 문서에서도 reload 루프를 막고 쿨다운 뒤 다시 복구한다", () => {
  const storage = fakeStorage();
  storage.setItem(serviceWorkerUpdateModule.STALE_CHUNK_RELOAD_STORAGE_KEY, "10000");
  let reloadCount = 0;

  const reloadedDocument = fakeEventTarget();
  serviceWorkerUpdateModule.installStaleChunkReload(
    reloadedDocument,
    storage,
    () => reloadCount++,
    () => 10_001,
  );
  reloadedDocument.dispatch("vite:preloadError", { preventDefault() {} });
  assert.equal(reloadCount, 0, "같은 세션의 즉시 재실패는 reload하지 않아야 한다");

  const laterDocument = fakeEventTarget();
  serviceWorkerUpdateModule.installStaleChunkReload(
    laterDocument,
    storage,
    () => reloadCount++,
    () => 10_000 + serviceWorkerUpdateModule.STALE_CHUNK_RELOAD_COOLDOWN_MS,
  );
  laterDocument.dispatch("vite:preloadError", { preventDefault() {} });
  assert.equal(reloadCount, 1, "쿨다운 뒤의 별도 배포 실패는 다시 복구할 수 있어야 한다");
});

test("sessionStorage가 없는 WebView는 history.state fallback으로 reload 루프를 막는다", () => {
  assertHistoryFallbackPreventsReloadLoop(null);
});

test("sessionStorage 접근이 예외를 내도 history.state fallback으로 reload 루프를 막는다", () => {
  const throwingStorage = {
    getItem() {
      throw new Error("sessionStorage is unavailable");
    },
    setItem() {
      throw new Error("sessionStorage is unavailable");
    },
  };
  assertHistoryFallbackPreventsReloadLoop(throwingStorage);
});

test("서비스워커 미지원과 최초 설치는 불필요한 새로고침을 하지 않는다", () => {
  let reloadCount = 0;
  serviceWorkerUpdateModule.installServiceWorkerUpdateReload(null, () => reloadCount++);

  const serviceWorker = fakeServiceWorker(null);
  serviceWorkerUpdateModule.installServiceWorkerUpdateReload(serviceWorker, () => reloadCount++);
  serviceWorker.changeController({ version: "initial" });

  assert.equal(reloadCount, 0);
});

test("기존 서비스워커 교체는 controller identity가 바뀔 때 한 번만 새로고침한다", () => {
  let reloadCount = 0;
  const serviceWorker = fakeServiceWorker({ version: "old" });
  serviceWorkerUpdateModule.installServiceWorkerUpdateReload(serviceWorker, () => reloadCount++);

  serviceWorker.changeController(null);
  serviceWorker.changeController(serviceWorker.controller);
  assert.equal(reloadCount, 0);

  serviceWorker.changeController({ version: "new" });
  serviceWorker.changeController({ version: "newer" });
  assert.equal(reloadCount, 1);
});

test("서비스워커 갱신 감시는 자동 업데이트 등록과 React 렌더보다 먼저 연결된다", () => {
  assert.match(viteConfigSource, /registerType:\s*'autoUpdate'/);
  assert.match(mainSource, /installStaleChunkReload\(\);/);
  assert.ok(
    mainSource.indexOf("installStaleChunkReload();") < mainSource.indexOf("createRoot("),
    "Vite preload 오류 감시는 React 렌더링 전에 설치되어야 한다",
  );
  assert.ok(
    mainSource.indexOf("installServiceWorkerUpdateReload();") < mainSource.indexOf("createRoot("),
    "서비스워커 갱신 감시는 React 렌더링 전에 설치되어야 한다",
  );
});
