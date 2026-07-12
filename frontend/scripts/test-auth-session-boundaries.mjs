import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (relative) => readFile(new URL(`../${relative}`, import.meta.url), "utf8");
const [api, collaboration, planner, autoPrep, messenger, auth] = await Promise.all([
  read("src/app/lib/api.ts"),
  read("src/features/collaboration/api/collaborationApi.ts"),
  read("src/features/planner/api/plannerApi.ts"),
  read("src/features/autoprep/api/autoPrepApi.ts"),
  read("src/features/collaboration/pages/MessengerPage.tsx"),
  read("src/app/auth/AuthContext.tsx"),
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

console.log("authenticated raw/download/account boundary contracts: ok");
