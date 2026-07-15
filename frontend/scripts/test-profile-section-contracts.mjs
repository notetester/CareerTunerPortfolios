import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const [sections, routes, profileApi, profilePage] = await Promise.all([
  readFile("src/features/profile/pages/ProfileSectionPages.tsx", "utf8"),
  readFile("src/app/routes.ts", "utf8"),
  readFile("src/app/profile/profileApi.ts", "utf8"),
  readFile("src/app/pages/Profile.tsx", "utf8"),
]);

const resumeStart = sections.indexOf("export function ProfileResumePage");
const selfIntroStart = sections.indexOf("export function ProfileSelfIntroductionPage");
const experienceStart = sections.indexOf("export function ProfileExperiencePage");
const resumeSource = sections.slice(resumeStart, selfIntroStart);
const selfIntroSource = sections.slice(selfIntroStart, experienceStart);
const hookSource = sections.slice(0, sections.indexOf("const profileDestinations"));

assert(resumeStart >= 0 && selfIntroStart > resumeStart && experienceStart > selfIntroStart,
  "독립 이력서·자기소개서·경력 페이지 export가 필요하다");
assert.match(routes, /path: "profile\/resume", Component: AuthenticatedProfileResumePage/);
assert.match(routes, /path: "profile\/self-introduction", Component: AuthenticatedProfileSelfIntroductionPage/);

for (const contract of [
  "uploadProfileFile",
  "importProfileDocument",
  "startProfileAnalyze",
  "pollProfileAnalyze",
  "deleteUnlinkedProfileFile",
]) {
  assert.match(resumeSource, new RegExp(`\\b${contract}\\b`), `이력서 페이지에서 ${contract} 계약을 사용해야 한다`);
}
assert.match(resumeSource, /resumeAnalysisAgreed/,
  "문서 업로드 전 이력서 분석 개인정보 동의를 확인해야 한다");
assert.match(resumeSource, /context\.baselineProfile\.versionNo \?\? null/,
  "이력서 import는 최초 로드 버전이 아니라 operation 기준 버전을 보내야 한다");
assert.match(resumeSource, /finally \{[\s\S]*deleteUnlinkedProfileFile\(uploadedFileId\)/,
  "이력서 처리 원본은 성공·실패와 무관하게 finally에서 즉시 삭제해야 한다");
assert.match(resumeSource, /StructuredAnalysisCard/,
  "구조화 결과는 사용자 선택 UI를 거쳐야 한다");
assert.match(resumeSource, /persistPatch\(\(baseline\) =>/,
  "구조화 결과도 section 기준본과 3-way 충돌 검사를 거쳐야 한다");

for (const contract of ["uploadProfileFile", "importProfileDocument", "deleteUnlinkedProfileFile"]) {
  assert.match(selfIntroSource, new RegExp(`\\b${contract}\\b`), `자기소개서 페이지에서 ${contract} 계약을 사용해야 한다`);
}
assert.match(selfIntroSource, /"SELF_INTRO"/,
  "자기소개서 문서는 SELF_INTRO target으로 가져와야 한다");
assert.match(selfIntroSource, /context\.baselineProfile\.versionNo \?\? null/,
  "자기소개서 import도 baseVersionNo를 보내야 한다");
assert.match(selfIntroSource, /finally \{[\s\S]*deleteUnlinkedProfileFile\(uploadedFileId\)/,
  "자기소개서 처리 원본도 finally에서 즉시 삭제해야 한다");

for (const contract of [
  "profileRef",
  "baselineDraftRef",
  "draftRef",
  "loadEpochRef",
  "operationEpochRef",
  "operationInFlightRef",
  "mergeProfileSectionPatch",
  "rebaseDraftAfterCommit",
]) {
  assert.match(hookSource, new RegExp(`\\b${contract}\\b`), `section controller에 ${contract} 경계가 필요하다`);
}
assert.match(hookSource, /addEventListener\("beforeunload"/,
  "브라우저 새로고침·창 닫기에서 미저장 입력을 보호해야 한다");
assert.match(hookSource, /const guardActive = isDirty \|\| saving/,
  "빈 draft라도 저장·문서 처리 중에는 이탈 보호를 유지해야 한다");
assert.match(hookSource, /useBlocker\(/,
  "React Router 이동에서 미저장 입력을 보호해야 한다");
assert.match(hookSource, /registerNativeExitGuard\(\(\) => window\.confirm\(leaveMessage\)\)/,
  "네이티브 cold-start 종료에서도 미저장 입력을 보호해야 한다");
assert.match(hookSource, /dirtyRef\.current && !window\.confirm\(UNSAVED_SECTION_MESSAGE\)/,
  "다시 불러오기는 미저장 입력 폐기를 확인받아야 한다");
assert.match(hookSource, /applyCommittedProfile\(saved, operation\.requestStartDraft\)/,
  "일반 저장 응답은 저장 중 추가 입력을 보존해 반영해야 한다");
assert.match(hookSource, /applyCommittedProfile\(saved, operation\.baselineDraft\)/,
  "구조화 patch 저장은 기존 section 미저장 입력까지 보존해야 한다");
assert.match(hookSource, /if \(!dirtyRef\.current\)[\s\S]*?저장할 변경사항이 없습니다/,
  "변경 없는 저장은 PUT과 version 증가 없이 종료해야 한다");
assert.match(hookSource, /conflictBaseline \?\? operation\.baselineProfile/,
  "오래 걸린 구조화 분석은 적용 시점 profileRef가 아니라 생성 기준본으로 충돌을 검사해야 한다");
assert.match(resumeSource, /analysisBaselineRef\.current = imported\.profile/,
  "구조화 초안과 import 완료 profile snapshot을 함께 보관해야 한다");
assert.match(resumeSource, /analysisBaseline\);/,
  "구조화 patch 적용 시 생성 기준 profile을 persist 경계에 전달해야 한다");
assert.match(selfIntroSource, /state\.writeConflict \|\| !resumeAnalysisAgreed/,
  "자기소개서 import도 충돌 해소 전에는 다시 시작할 수 없어야 한다");

assert.match(profileApi, /export const PROFILE_IMPORT_PENDING_REF_TYPE = "PROFILE_IMPORT_PENDING"/);
assert.match(profileApi, /fd\.append\("refType", PROFILE_IMPORT_PENDING_REF_TYPE\)/,
  "import 원본은 강제 종료 시 서버 TTL 정리 대상 refType으로 업로드해야 한다");

assert.match(profilePage, /export interface ProfilePageProps[\s\S]*?mode\?: "full" \| "ai-analysis"/,
  "기존 프로필 편집기는 AI 전용 entry에서 재사용할 명시적 mode 계약이 필요하다");
assert.match(profilePage, /const aiOnly = mode === "ai-analysis"/);
assert.match(profilePage, /aiOnly[\s\S]*?lastSavedFormRef\.current, skillsText: form\.skillsText, selfIntro: form\.selfIntro/,
  "AI 전용 화면은 결과 반영이 가능한 스킬·자기소개만 저장 payload에 포함해야 한다");
assert.match(profilePage, /saveProfile\(toRequest\(payloadForm, profileVersionNoRef\.current\)\)/,
  "AI 전용 저장도 기존 optimistic profile version 계약을 우회하면 안 된다");
assert.match(profilePage, /\{!aiOnly && \([\s\S]*?<TabsList/,
  "AI 전용 화면에서는 숨겨진 CRUD 탭을 노출하면 안 된다");
assert.match(profilePage, /export function ProfileAiAnalysisPage\(\)[\s\S]*?<ProfilePage mode="ai-analysis"/,
  "AI 분석 literal route entry가 mode를 고정해야 한다");

console.log("profile section concurrency, import lifecycle and navigation contracts: ok");
