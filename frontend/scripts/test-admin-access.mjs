import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const read = (path) => readFileSync(resolve(root, path), "utf8");

const accessSource = read("src/admin/auth/adminAccess.ts");
const boundarySource = read("src/admin/auth/AdminRouteBoundary.tsx");
const routesSource = read("src/admin/routes.ts");
const shellSource = read("src/admin/components/AdminShell.tsx");
const mockSource = read("src/app/lib/mock/index.ts");
const apiSource = read("src/app/lib/api.ts");
const authSource = read("src/app/auth/AuthContext.tsx");
const tokenStoreSource = read("src/app/lib/tokenStore.ts");

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

test("SUPER_ADMIN 전용 네 경로는 route metadata에서 page mount 전에 닫힌다", () => {
  const superOnly = [...accessSource.matchAll(/^\s{2}(?:"([^"]+)"|admin):\s*\{[^\n]*superOnly:\s*true[^\n]*\}/gm)]
    .map((match) => match[1] ?? "admin");
  assert.deepEqual(sorted(superOnly), sorted([
    "admin/super",
    "admin/policies",
    "admin/runtime-settings",
    "admin/staff-grades",
  ]));
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

test("mock /admin API와 outage 관리자 persona를 중앙 차단한다", () => {
  assert.match(mockSource, /export function canResolveMockRequest/);
  assert.match(mockSource, /if \(!canResolveMockRequest\(rawPath\)\) return MOCK_FORBIDDEN;/);
  assert.equal((apiSource.match(/value === mock\.MOCK_FORBIDDEN/g) ?? []).length, 2);
  assert.match(mockSource, /getOutageFallbackSnapshot\(\)\.mode !== "static-demo"/);
  assert.match(mockSource, /email === demoAdminUser\.email/);
  assert.match(accessSource, /"\/admin\/policies"/);
});

test("token 변경은 role을 /auth/me로 재검증하고 AdminShell은 실제 사용자를 표시한다", () => {
  assert.match(authSource, /if \(event === "cleared"\)[\s\S]*void refreshMe\(\);/);
  assert.match(authSource, /const generation = \+\+refreshGeneration\.current;/);
  assert.match(shellSource, /\{displayName\} · \{displayRole\}/);
  assert.doesNotMatch(shellSource, /<span className="adm__profile-name">관리자<\/span>/);
  assert.match(shellSource, /useAdminPendingCounts\(canUseAdmin\)/);
  assert.match(shellSource, /if \(!canUseAdmin\)/);
});

test("이전 세션의 비동기 응답은 새 로그인 토큰이나 사용자를 덮어쓰지 않는다", () => {
  assert.match(tokenStoreSource, /let revision = 0;/);
  assert.match(tokenStoreSource, /export function setTokensIfUnchanged/);
  assert.match(tokenStoreSource, /export function clearTokensIfUnchanged/);
  assert.match(tokenStoreSource, /revision !== expected\.revision/);
  assert.match(apiSource, /interface RefreshAttempt/);
  assert.match(apiSource, /refreshAttempt\.snapshot\.revision === snapshot\.revision/);
  assert.match(apiSource, /return setTokensIfUnchanged\(snapshot,/);
  assert.match(apiSource, /clearTokensIfUnchanged\(snapshot\);/);
  assert.match(authSource, /generation === refreshGeneration\.current[\s\S]*isTokenStoreSnapshotCurrent\(sessionSnapshot\)/);
  assert.match(authSource, /setUser\(null\);\s*clearTokensIfUnchanged\(sessionSnapshot\);/);
});
