import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (relative) => readFile(new URL(`../${relative}`, import.meta.url), "utf8");
const [api, collaboration, planner, autoPrep, messenger, auth, authGate, routes, profilePage, profileMock] = await Promise.all([
  read("src/app/lib/api.ts"),
  read("src/features/collaboration/api/collaborationApi.ts"),
  read("src/features/planner/api/plannerApi.ts"),
  read("src/features/autoprep/api/autoPrepApi.ts"),
  read("src/features/collaboration/pages/MessengerPage.tsx"),
  read("src/app/auth/AuthContext.tsx"),
  read("src/app/auth/ConsentGate.tsx"),
  read("src/app/routes.ts"),
  read("src/app/pages/Profile.tsx"),
  read("src/app/lib/mock/domains/profile.ts"),
]);

assert.match(api, /export async function apiRaw/);
assert.match(api, /subscribeTokenStore/);
assert.match(api, /event !== "refreshed"/);
const blobRead = api.indexOf("const blob = await res.blob()");
assert(blobRead >= 0 && api.indexOf("assertAuthenticatedRequestStillCurrent(sessionSnapshot)", blobRead) > blobRead,
  "파일 본문 소비 뒤에도 계정 snapshot을 다시 검증해야 한다");

for (const [name, source] of [["협업 첨부", collaboration], ["플래너 ICS", planner]]) {
  assert.match(source, /apiBlob\(/, `${name} 다운로드는 중앙 인증 blob 경계를 사용해야 한다`);
  assert.doesNotMatch(source, /getAccessToken|await fetch\(/, `${name} 다운로드가 토큰 snapshot 검증을 우회하면 안 된다`);
}

assert.match(autoPrep, /apiRaw\("\/auto-prep\/run\/stream"/);
assert.match(autoPrep, /rawResponse\.assertSessionCurrent\(\)/);
assert.doesNotMatch(autoPrep, /getAccessToken/);
assert.match(messenger, /messageRequestSequence/);
assert.match(messenger, /activeConversationIdRef\.current === conversationId/);
assert.match(auth, /disablePush\(\)/);
assert.match(authGate, /export function AuthenticatedRouteBoundary/);
assert.match(authGate, /\/login\?returnTo=\$\{encodeURIComponent\(returnTo\)\}/);
assert.match(routes, /const AuthenticatedProfilePage = withAuthGate\(ProfilePage\)/);
assert.match(routes, /const AuthenticatedProfileDetailPage = withAuthGate\(ProfileDetailPage\)/);
assert.match(routes, /const AuthenticatedSettingsPage = withAuthGate\(SettingsPage\)/);
for (const component of [
  "ApplicationsPage",
  "ApplicationDetailPage",
  "MfaApprovalsPage",
  "MessengerPage",
  "CommunityActivityPage",
  "RewardsPage",
  "BillingPage",
  "BillingSuccessPage",
  "BillingFailPage",
  "CompanyHubPage",
  "NotificationPage",
]) {
  assert.match(routes, new RegExp(`const Authenticated\\w+ = withAuthGate\\(${component}\\)`),
    `${component} 회원 전용 화면은 공통 인증 경계로 감싸야 한다`);
  assert.doesNotMatch(routes, new RegExp(`Component: ${component}[^A-Za-z]`),
    `${component}를 익명 접근 가능한 원본 컴포넌트로 직접 mount하면 안 된다`);
}
assert.doesNotMatch(routes, /path: "profile", Component: ProfilePage/);
assert.doesNotMatch(routes, /path: "settings", Component: SettingsPage/);
assert.doesNotMatch(profilePage, /profileVersionNo:\s*versions\[0\]/,
  "AI 즉시 응답의 분석 버전을 최신 목록 후조회 값으로 덮어쓰면 안 된다");
assert.match(profilePage, /setSummaryResult\(result\)/);
assert.match(profilePage, /setSkillsResult\(result\)/);
assert.match(profileMock, /profileVersionId:\s*demoProfileVersions\[0\]\?\.id/,
  "mock AI 응답도 실행 시점의 프로필 버전을 반환해야 한다");

console.log("authenticated raw/download/account boundary contracts: ok");
