import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const profileSource = await readFile(
  new URL("../src/app/pages/Profile.tsx", import.meta.url),
  "utf8",
);
const profileDraftMergeSource = await readFile(
  new URL("../src/app/profile/profileDraftMerge.ts", import.meta.url),
  "utf8",
);
const profileApiSource = await readFile(
  new URL("../src/app/profile/profileApi.ts", import.meta.url),
  "utf8",
);
const onboardingGuideSource = await readFile(
  new URL("../src/features/support/hooks/useOnboardingGuide.ts", import.meta.url),
  "utf8",
);
const onboardingApiSource = await readFile(
  new URL("../src/features/support/api/onboardingApi.ts", import.meta.url),
  "utf8",
);

const sourceBetween = (start, end) => {
  const startIndex = profileSource.indexOf(start);
  const endIndex = profileSource.indexOf(end, startIndex + start.length);
  assert(startIndex >= 0, `시작 경계를 찾지 못했습니다: ${start}`);
  assert(endIndex > startIndex, `종료 경계를 찾지 못했습니다: ${end}`);
  return profileSource.slice(startIndex, endIndex);
};

assert.equal(
  profileSource.match(/diagnoseProfileCompleteness\(/g)?.length ?? 0,
  1,
  "완성도 AI 진단은 사용자가 실행하는 runAi 경로 한 곳에만 있어야 한다",
);

assert.match(profileSource, /const \[profileLoaded, setProfileLoaded\] = useState\(false\)/);
assert.match(profileSource, /const saveDisabled = loading \|\| !profileLoaded \|\| saving \|\| docImporting \|\| profileWriteConflict/);

const loadBody = sourceBetween("  const load = async () => {", "  useEffect(() => {");
assert.match(loadBody, /setProfileLoaded\(false\)/, "GET 시작과 실패 시 저장 게이트를 닫아야 한다");
assert.match(loadBody, /const profile = await getProfile\(\)/);
assert.match(loadBody, /profileVersionNoRef\.current = typeof profile\.versionNo === "number"/);
assert.ok(
  loadBody.indexOf("const profile = await getProfile()") < loadBody.indexOf("setProfileLoaded(true)"),
  "프로필 GET 성공 전에는 저장 게이트를 열면 안 된다",
);
assert.ok(
  loadBody.indexOf("setLoading(false)") < loadBody.indexOf("Promise.allSettled("),
  "프로필 본문 GET이 성공하면 부가 목록 조회 완료 전에도 저장 게이트를 열어야 한다",
);
assert.match(loadBody, /loadEpoch !== profileLoadEpochRef\.current/, "늦게 끝난 이전 새로고침이 최신 폼을 덮으면 안 된다");

const saveBody = sourceBetween(
  "  const save = async (showSuccess = true, scope?: Exclude<ProfileTab, \"ai\">): Promise<boolean> => {",
  "  const uploadPortfolio",
);
assert.match(saveBody, /if \(!profileLoaded \|\| loading \|\| docImporting \|\| saveInFlightRef\.current\)/);
assert.match(
  saveBody,
  /saveProfile\(toRequest\(payloadForm, profileVersionNoRef\.current\)\)/,
  "PUT에는 GET 또는 직전 저장 응답의 versionNo를 전달해야 한다",
);
assert.match(saveBody, /profileVersionNoRef\.current = savedProfile\.versionNo/);
assert.match(saveBody, /clearProfileAiResults\(\)/, "프로필 저장 후 이전 AI 결과를 현재 결과처럼 남기면 안 된다");

const saveCallIndex = saveBody.indexOf("await saveProfile(");
const baselineIndex = saveBody.indexOf("setSavedSnapshot(", saveCallIndex);
const successIndex = saveBody.indexOf("setMessage(", baselineIndex);
const versionRefreshIndex = saveBody.indexOf("void getProfileVersions()", baselineIndex);
assert(saveCallIndex >= 0 && baselineIndex > saveCallIndex, "PUT 성공 뒤 저장 기준본을 먼저 확정해야 한다");
assert(successIndex > baselineIndex, "저장 기준본 확정 뒤 성공 메시지를 표시해야 한다");
assert(versionRefreshIndex > successIndex, "저장 이력 후조회는 저장 성공 처리보다 뒤여야 한다");
assert.match(
  saveBody.slice(versionRefreshIndex),
  /\.catch\(\(\) => setNonFatalWarning\(/,
  "저장 이력 후조회 실패는 별도 비치명 경고로 처리해야 한다",
);

const importBody = sourceBetween(
  "  const handleDocumentAttach = async (file: File, target: ProfileImportTarget) => {",
  "  /** 확인 카드",
);
assert.match(importBody, /if \(!profileLoaded \|\| loading \|\| profileWriteConflict\)/);
assert.match(importBody, /profileVersionNoRef\.current = imported\.profile\.versionNo/);
assert.match(importBody, /clearProfileAiResults\(\)/);
assert.match(
  importBody,
  /importProfileDocument\([\s\S]*?uploaded\.id,[\s\S]*?target,[\s\S]*?profileVersionNoRef\.current,[\s\S]*?\)/,
  "문서 import는 화면이 읽은 profile version을 baseVersionNo로 전달해야 한다",
);
assert.match(
  profileApiSource,
  /importProfileDocument\([\s\S]*?baseVersionNo: number \| null,[\s\S]*?JSON\.stringify\(\{ fileId, target, baseVersionNo \}\)/,
  "프로필 import API 요청 본문에 baseVersionNo가 포함되어야 한다",
);
assert.match(
  onboardingGuideSource,
  /const profileBeforeImport = await getProfile\(\);[\s\S]*?importProfileDocument\([\s\S]*?"RESUME_TEXT",[\s\S]*?profileBeforeImport\.versionNo \?\? null/,
  "온보딩 이력서 import도 직전에 읽은 프로필 버전을 전달해야 한다",
);
assert.match(
  onboardingGuideSource,
  /const profileBeforeImport = await getProfile\(\);[\s\S]*?importProfileDocument\([\s\S]*?"SELF_INTRO",[\s\S]*?profileBeforeImport\.versionNo \?\? null/,
  "온보딩 자기소개서 import도 직전에 읽은 프로필 버전을 전달해야 한다",
);
assert.match(
  profileApiSource,
  /PROFILE_IMPORT_PENDING_REF_TYPE\s*=\s*"PROFILE_IMPORT_PENDING"/,
  "프로필 import 업로드는 강제 종료 시 식별할 명시적 pending 용도를 가져야 한다",
);
assert.match(
  profileApiSource,
  /fd\.append\("refType", PROFILE_IMPORT_PENDING_REF_TYPE\)/,
  "업로드 요청에 PROFILE_IMPORT_PENDING refType을 포함해야 한다",
);
assert.match(
  onboardingApiSource,
  /PROFILE_IMPORT_PENDING_REF_TYPE\s*=\s*"PROFILE_IMPORT_PENDING"[\s\S]*?fd\.append\("refType", refType\)/,
  "온보딩 문서 업로드 API도 명시적인 pending refType을 전송해야 한다",
);
assert.match(
  onboardingGuideSource,
  /slot === "resume" \? PROFILE_IMPORT_PENDING_REF_TYPE : AUTO_PREP_PENDING_REF_TYPE/,
  "온보딩 이력서는 PROFILE_IMPORT_PENDING TTL 정리 경로에 등록해야 한다",
);
assert.match(importBody, /let uploadedFileId: number \| null = null/);
assert.match(importBody, /uploadedFileId = uploaded\.id/);
const importFinallyBody = importBody.slice(importBody.lastIndexOf("} finally {"));
assert.match(
  importFinallyBody,
  /if \(uploadedFileId !== null\)[\s\S]*?await deleteUnlinkedProfileFile\(uploadedFileId\)/,
  "import/analyze 성공·실패와 관계없이 업로드 원본을 finally에서 정리해야 한다",
);
assert.match(
  importFinallyBody,
  /서버가 24시간 후 자동으로 정리합니다/,
  "즉시 정리 실패는 TTL 회수 경로를 사용자에게 알려야 한다",
);

assert.match(profileSource, /window\.addEventListener\("beforeunload", handler\)/);
assert.match(profileSource, /const profileExitGuardActive = isDirty \|\| saving \|\| docImporting/,
  "변경사항이 아직 없어도 저장·문서 처리 중에는 이탈 보호가 유지돼야 한다");
assert.match(profileSource, /const blocker = useBlocker\(/);
assert.match(profileSource, /profileExitGuardActive && currentLocation\.pathname !== nextLocation\.pathname/);
assert.match(profileSource, /blocker\.proceed\(\)/);
assert.match(profileSource, /blocker\.reset\(\)/);
assert.match(profileSource, /registerNativeExitGuard\(\(\) => window\.confirm\(profileExitMessage\)\)/,
  "history가 없는 Capacitor 앱 종료도 미저장 확인을 거쳐야 한다");
assert.match(saveBody, /err instanceof ApiError && err\.status === 409[\s\S]*?setProfileWriteConflict\(true\)/,
  "optimistic version 충돌은 입력을 보존하고 후속 저장을 닫아야 한다");
const refreshBody = sourceBetween("  const refreshProfile = () => {", "  const changeProfileTab");
assert.match(refreshBody, /if \(docImporting\) return/);
assert.match(refreshBody, /isDirty && !window\.confirm\(UNSAVED_PROFILE_MESSAGE\)/);
assert.match(refreshBody, /void load\(\)/);

assert.match(profileSource, /disabled=\{saveDisabled\}/, "저장 버튼은 로딩·저장 불가 상태를 함께 반영해야 한다");
assert.match(profileSource, /disabled=\{saveDisabled \|\| !!aiLoading \|\| !profileAiAllowed\}/);
assert.match(profileSource, /const analysisEpoch = profileAnalysisEpochRef\.current/);
assert.match(profileSource, /analysisEpoch !== profileAnalysisEpochRef\.current/,
  "프로필 저장 뒤 늦게 도착한 이전 AI 결과를 다시 표시하면 안 된다");
assert.match(profileSource, /currentVersionNo !== resultVersionNo/,
  "AI 응답의 입력 프로필 버전이 현재 버전과 다르면 결과를 폐기해야 한다");
assert.doesNotMatch(
  profileSource,
  /profileVersionNoRef\.current\s*=\s*resultVersionNo/,
  "GET 당시 행이 없던 화면이 AI 응답 버전만 채택해 외부 생성 프로필을 덮으면 안 된다",
);
assert.match(
  importBody,
  /const importBaseline = lastSavedFormRef\.current/,
  "문서 import 시작 시 3-way merge 기준본을 고정해야 한다",
);
assert.match(
  importBody,
  /const importLocalStart = form/,
  "문서 import 확인 시점의 로컬 대상값을 고정해야 한다",
);
assert.match(
  importBody,
  /mergeServerProfileForm\([\s\S]*?formRef\.current,[\s\S]*?importBaseline,[\s\S]*?serverForm,[\s\S]*?importLocalStart,[\s\S]*?importedField/,
  "문서 import 응답은 서버 최신값과 import 중 로컬 편집을 3-way merge해야 한다",
);
assert.match(profileSource, /function mergeServerProfileForm\(/);
assert.match(profileSource, /key === importedField \? localAtImportStart\[key\] : baseline\[key\]/,
  "확인 전에 존재한 대상 입력은 import 결과로 교체하고, 대기 중 새 편집만 보존해야 한다");
assert.match(profileSource, /localChanged && serverChanged && JSON\.stringify\(local\[key\]\) !== JSON\.stringify\(server\[key\]\)/,
  "같은 필드를 로컬과 서버가 모두 바꾼 3-way 충돌을 감지해야 한다");
assert.match(importBody, /setProfileWriteConflict\(mergeResult\.conflicted\)/);
assert.match(importBody, /if \(mergeResult\.conflicted\)[\s\S]*?기존 기준 버전을 유지/,
  "3-way 충돌 시 최신 baseVersion을 채택해 후속 저장이 통과하면 안 된다");
assert.match(
  profileDraftMergeSource,
  /versionNo:\s*cur\.versionNo/,
  "승인된 문서 초안의 GET→merge→PUT도 읽은 프로필 버전을 보존해야 한다",
);

console.log("profile save, version, and navigation boundaries: ok");
