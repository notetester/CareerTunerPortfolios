import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { transformWithOxc } from "vite";

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function importTypeScriptModule(relativePath) {
  const source = await readFile(resolve(relativePath), "utf8");
  const { code } = await transformWithOxc(source, relativePath, {
    lang: "ts",
    target: "es2022",
  });
  const encoded = Buffer.from(code, "utf8").toString("base64");
  return import(`data:text/javascript;base64,${encoded}`);
}

const submission = await importTypeScriptModule("src/features/interview/lib/mobileSubmission.ts");
const original = { kind: "question", id: 1 };
const pendingAnswer = { kind: "answer", submissionId: "pending-1" };
const pendingScoring = { kind: "scoring", submissionId: "pending-1" };
const anotherPending = { kind: "answer", submissionId: "pending-2" };
const rolledBack = submission.rollbackOptimisticSubmission(
  [original, pendingAnswer, pendingScoring, anotherPending],
  "pending-1",
);
assert(rolledBack.length === 2, "실패한 요청의 임시 항목 두 개만 제거해야 한다");
assert(rolledBack.includes(original), "기존 스레드 항목을 보존해야 한다");
assert(rolledBack.includes(anotherPending), "다른 요청의 임시 항목을 보존해야 한다");
assert(submission.restoreFailedDraft("", "보존할 답변") === "보존할 답변", "빈 초안에는 실패한 답변을 복구해야 한다");
assert(submission.restoreFailedDraft("새 입력", "이전 답변") === "새 입력", "새 입력을 실패한 답변으로 덮어쓰면 안 된다");
const audioFields = submission.buildPendingMediaAnswerFields("AUDIO", 71, "/api/file/71/content");
assert(audioFields.audioFileId === 71 && audioFields.audioUrl === "/api/file/71/content", "음성 pending 파일 ID와 URL을 answers 계약에 함께 넣어야 한다");
assert(!("videoFileId" in audioFields), "음성 제출에 영상 파일 ID가 섞이면 안 된다");
const videoFields = submission.buildPendingMediaAnswerFields("VIDEO", 72, "/api/file/72/content");
assert(videoFields.videoFileId === 72 && videoFields.videoUrl === "/api/file/72/content", "영상 pending 파일 ID와 URL을 answers 계약에 함께 넣어야 한다");
assert(!("audioFileId" in videoFields), "영상 제출에 음성 파일 ID가 섞이면 안 된다");

const server = await importTypeScriptModule("src/features/settings/lib/serverAddress.ts");
const emulator = server.resolveServerOverride("emulator", "", true);
assert(emulator.override === "http://10.0.2.2:8080/api", "에뮬레이터는 호스트 PC 특수 주소를 사용해야 한다");
const aws = server.resolveServerOverride("aws", "");
assert(aws.override === "https://careertuner.kro.kr/api", "AWS 프리셋은 실제 HTTPS API를 사용해야 한다");
assert(!server.SERVER_PRESETS.some((item) => item.url?.includes("CHANGEME")), "플레이스홀더 주소가 남으면 안 된다");
assert(server.resolveServerOverride("custom", "file:///tokens").error !== null, "http(s) 외 URL을 거부해야 한다");
assert(server.resolveServerOverride("custom", "https://user:pass@example.com/api").error !== null, "URL 인증 정보를 거부해야 한다");
assert(server.resolveServerOverride("custom", "http://example.com/api").error !== null, "공개 HTTP 서버를 거부해야 한다");
assert(server.resolveServerOverride("custom", "http://192.168.0.10:8080/api").error !== null, "운영 빌드는 사설 LAN HTTP도 거부해야 한다");
assert(server.resolveServerOverride("custom", "http://192.168.0.10:8080/api", true).error === null, "명시적인 개발 빌드는 사설 LAN 서버를 허용해야 한다");
assert(server.resolveServerOverride("custom", "http://localhost:8080/api").error === null, "진짜 loopback HTTP는 허용해야 한다");
assert(server.resolveServerOverride("custom", "https://example.com/not-api").error !== null, "API가 아닌 경로를 거부해야 한다");
assert(server.resolveServerOverride("custom", "https://example.com/api?token=value").error !== null, "query가 포함된 주소를 거부해야 한다");
assert(!server.serverOverrideChanged("https://example.com/api/", "https://example.com/api"), "끝 슬래시만 다른 주소는 동일해야 한다");
assert(server.serverOverrideChanged(null, aws.override), "빌드 기본값과 AWS 오버라이드는 변경으로 판단해야 한다");

const apiBaseSource = await readFile(resolve("src/app/lib/apiBase.ts"), "utf8");
assert(apiBaseSource.includes('resolveServerOverride("custom", stored'), "저장된 override도 읽을 때 정책 검증해야 한다");
assert(apiBaseSource.includes("clearTokens();"), "안전하지 않은 기존 override는 토큰과 함께 폐기해야 한다");

const appLock = await importTypeScriptModule("src/platform/appLockState.ts");
const initialGeneration = appLock.captureAppLockGeneration();
assert(initialGeneration !== null, "초기 해제 상태에서는 민감 작업 세대를 발급해야 한다");
assert(appLock.updateAppLockState(true), "잠금 전환은 세대를 변경해야 한다");
assert(!appLock.updateAppLockState(true), "동일 잠금 상태를 중복 적용해 세대를 흔들면 안 된다");
assert(!appLock.isAppLockGenerationCurrent(initialGeneration), "잠금 전 작업은 즉시 무효화해야 한다");
let discardedResources = 0;
assert(!appLock.guardAppLockGeneration(initialGeneration, () => { discardedResources += 1; }), "만료 세대 자원은 유지하면 안 된다");
assert(discardedResources === 1, "만료 세대 자원은 즉시 한 번 폐기해야 한다");
assert(appLock.captureAppLockGeneration() === null, "잠금 중에는 새 민감 작업을 시작하면 안 된다");
assert(appLock.updateAppLockState(false), "잠금 해제도 새 세대를 만들어야 한다");
const resumedGeneration = appLock.captureAppLockGeneration();
assert(resumedGeneration !== null && resumedGeneration !== initialGeneration, "해제 후 작업은 새 세대를 사용해야 한다");

const directMediaFiles = [
  "src/features/interview/components/AvatarTab.tsx",
  "src/features/interview/components/LocalAvatarTab.tsx",
  "src/features/interview/components/LocalVoiceInterviewTab.tsx",
  "src/features/interview/components/RealtimeInterviewTab.tsx",
  "src/features/interview/components/mobile/ImmersiveAvatarOverlay.tsx",
  "src/features/interview/components/mobile/ImmersiveVoiceOverlay.tsx",
  "src/features/interview/pages/MicRemotePage.tsx",
];
for (const mediaFile of directMediaFiles) {
  const source = await readFile(resolve(mediaFile), "utf8");
  assert(source.includes("captureAppLockGeneration"), `${mediaFile}는 시작 시 앱 잠금 세대를 캡처해야 한다`);
  assert(source.includes("keepStreamForAppLock"), `${mediaFile}는 획득한 스트림을 잠금 세대로 검증해야 한다`);
}
const mobileThreadSource = await readFile(resolve("src/features/interview/pages/MobileSessionThreadPage.tsx"), "utf8");
assert(mobileThreadSource.includes("rollbackOptimisticSubmission"), "모바일 답변 실패 시 낙관 항목 롤백을 유지해야 한다");
assert(mobileThreadSource.includes("restoreFailedDraft"), "모바일 답변 실패 시 입력 초안 복원을 유지해야 한다");
assert(mobileThreadSource.includes("deletePendingInterviewFile"), "교체·이탈 시 pending 면접 원본을 정리해야 한다");
assert(mobileThreadSource.includes("deleteAnswerMedia"), "저장된 답변 원본 삭제 UX가 API에 연결되어야 한다");
const voiceOverlaySource = await readFile(resolve("src/features/interview/components/mobile/ImmersiveVoiceOverlay.tsx"), "utf8");
const avatarOverlaySource = await readFile(resolve("src/features/interview/components/mobile/ImmersiveAvatarOverlay.tsx"), "utf8");
assert(voiceOverlaySource.includes("audioBlob: blob"), "음성 오버레이는 제출용 원본 Blob을 부모에 인계해야 한다");
assert(avatarOverlaySource.includes("videoBlob: blob"), "영상 오버레이는 제출용 원본 Blob을 부모에 인계해야 한다");
const prepromptSource = await readFile(resolve("src/features/interview/components/mobile/PermissionPreprompt.tsx"), "utf8");
assert(!prepromptSource.includes("원본은 즉시 폐기"), "권한 안내가 실제 원본 보관 동작과 충돌하면 안 된다");
assert(prepromptSource.includes("삭제 후에는 원본 기반 재분석이 불가능"), "원본 삭제의 재분석 제한을 동의 전에 알려야 한다");
assert(prepromptSource.includes("preprompt.v2"), "원본 보관 정책 변경 후에는 기존 즉시 폐기 동의를 재사용하면 안 된다");
assert(mobileThreadSource.includes("audioBlob?.size") && mobileThreadSource.includes("videoBlob?.size"), "STT가 비어도 캡처 원본은 pending 업로드해야 한다");
assert(mobileThreadSource.indexOf("setPendingMedia({ questionId, kind, file") < mobileThreadSource.indexOf("cleanupPendingFile(previous.file.id)"), "새 원본 저장이 확정되기 전에 이전 pending 원본을 삭제하면 안 된다");
const remoteMediaSource = await readFile(resolve("src/features/interview/components/RemoteMicConnectCard.tsx"), "utf8");
assert(remoteMediaSource.includes("useAppLockCleanup"), "원격 WebRTC 수신 연결도 앱 잠금 시 종료해야 한다");

console.log("PASS mobile platform tests");
