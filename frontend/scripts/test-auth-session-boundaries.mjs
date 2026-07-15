import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (relative) => readFile(new URL(`../${relative}`, import.meta.url), "utf8");
const [api, collaboration, planner, autoPrep, messenger, auth, authGate, routes, profilePage, profileApi, profileMock] = await Promise.all([
  read("src/app/lib/api.ts"),
  read("src/features/collaboration/api/collaborationApi.ts"),
  read("src/features/planner/api/plannerApi.ts"),
  read("src/features/autoprep/api/autoPrepApi.ts"),
  read("src/features/collaboration/pages/MessengerPage.tsx"),
  read("src/app/auth/AuthContext.tsx"),
  read("src/app/auth/ConsentGate.tsx"),
  read("src/app/routes.ts"),
  read("src/app/pages/Profile.tsx"),
  read("src/app/profile/profileApi.ts"),
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
assert.match(authGate, /pathname\.startsWith\("\/settings\/"\)/,
  "필수 동의 철회 사용자가 설정 하위 복구 화면에 진입할 수 있어야 한다");
for (const profileComponent of [
  "ProfileHubPage",
  "ProfileBasicPage",
  "ProfileResumePage",
  "ProfileSelfIntroductionPage",
  "ProfileExperiencePage",
  "ProfileSkillsPage",
  "ProfileCredentialsPage",
  "ProfileAiAnalysisPage",
]) {
  assert.match(routes, new RegExp(`const Authenticated\\w+ = withAuthGate\\(${profileComponent}\\)`),
    `${profileComponent} 회원 전용 화면은 공통 인증 경계로 감싸야 한다`);
  assert.doesNotMatch(routes, new RegExp(`Component: ${profileComponent}[^A-Za-z]`),
    `${profileComponent}를 익명 접근 가능한 원본 컴포넌트로 직접 mount하면 안 된다`);
}
assert.match(routes, /const AuthenticatedProfileDetailPage = withAuthGate\(ProfileDetailPage\)/);
assert.match(routes, /path: "profile\/ai-analysis", Component: AuthenticatedProfileAiAnalysisPage/,
  "AI 프로필 분석은 query 탭이 아닌 회원 전용 literal route여야 한다");
assert.match(profilePage, /export function ProfileAiAnalysisPage\(\)/,
  "기존 프로필 AI 기능을 독립 화면 엔트리로 노출해야 한다");
assert.match(profilePage, /if \(aiOnly\) \{\s*setActiveTab\("ai"\);\s*return;/,
  "독립 AI 분석 화면에서 CRUD query 탭으로 이동하면 안 된다");
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
  "CompanyServiceOverviewPage",
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
assert.match(profileApi, /versionNo, updatedAt: _updatedAt, \.\.\.editable/,
  "프로필 저장은 응답 전용 versionNo를 편집 필드와 분리해야 한다");
assert.match(profileApi, /baseVersionNo: versionNo \?\? null/,
  "프로필 PUT은 클라이언트가 읽은 버전을 기준 버전으로 보내야 한다");
assert.match(profileMock, /baseVersionNo !== demoProfile\.versionNo/,
  "mock 프로필 저장도 운영과 같은 stale 버전 거절 계약을 따라야 한다");
assert.match(profileMock, /new ApiError\([\s\S]*?"CONFLICT",\s*409/,
  "mock 프로필 stale 저장도 운영과 같은 CONFLICT/409 오류 형태를 반환해야 한다");
assert.match(profileMock, /profileVersionId:\s*demoProfileVersions\[0\]\?\.id/,
  "mock AI 응답도 실행 시점의 프로필 버전을 반환해야 한다");

console.log("authenticated raw/download/account boundary contracts: ok");
