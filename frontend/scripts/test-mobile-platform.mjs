import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { createServer, transformWithOxc } from "vite";

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
const nativePushConfiguration = await importTypeScriptModule("src/platform/nativePushConfigurationCore.ts");
assert(!nativePushConfiguration.shouldRegisterNativePush("android", false), "Firebase 미구성 Android는 네이티브 토큰 등록을 호출하면 안 된다");
assert(nativePushConfiguration.shouldRegisterNativePush("android", true), "Firebase가 초기화된 Android는 기존 FCM 등록을 유지해야 한다");
assert(!nativePushConfiguration.shouldRegisterNativePush("ios", true), "원시 APNs 토큰을 FCM 토큰으로 오등록하면 안 된다");
assert(!nativePushConfiguration.shouldRegisterNativePush("web", true), "네이티브 플랫폼 판별 실패를 토큰 등록 허용으로 처리하면 안 된다");
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
assert(submission.MOBILE_INTERVIEW_VIDEO_BITS_PER_SECOND === 1_200_000, "모바일 영상 recorder bitrate를 명시해야 한다");
assert(submission.MOBILE_INTERVIEW_AUDIO_BITS_PER_SECOND === 64_000, "모바일 음성 recorder bitrate를 명시해야 한다");
assert(submission.MOBILE_INTERVIEW_VIDEO_MAX_SECONDS === 45, "영상은 9MiB 안전 한도를 위한 최대 길이가 있어야 한다");
assert(submission.MOBILE_INTERVIEW_AUDIO_MAX_SECONDS === 180, "장시간 음성의 base64 메모리 사용을 제한해야 한다");
assert(submission.validateCapturedMediaSize(submission.MOBILE_INTERVIEW_MEDIA_MAX_BYTES - 1).ok, "9MiB 미만 원본은 허용해야 한다");
assert(submission.validateCapturedMediaSize(submission.MOBILE_INTERVIEW_MEDIA_MAX_BYTES).reason === "TOO_LARGE", "9MiB 이상 원본은 업로드 전에 차단해야 한다");
assert(submission.validateCapturedMediaSize(0).reason === "EMPTY", "빈 원본은 업로드하면 안 된다");
assert(submission.capturedDraft("  새 전사  ") === "새 전사", "원본과 교체할 전사를 정규화해야 한다");
assert(submission.capturedDraft("   ") === "", "빈 STT가 기존 원본의 초안과 섞이면 안 된다");
const cleanupTargets = submission.cleanupEligiblePendingFileIds([11, 12, 13, 13], [12]);
assert(cleanupTargets.join(",") === "11,13", "제출 중/결과 불명 파일은 pending cleanup에서 격리해야 한다");
assert(submission.settleMediaUploadGeneration(7, 7) === null, "완료한 upload generation은 indicator를 해제해야 한다");
assert(submission.settleMediaUploadGeneration(8, 7) === 8, "오래된 upload 완료가 새 generation을 지우면 안 된다");
assert(submission.classifySubmissionFailure({ status: 422, code: "INVALID_INPUT" }) === "DEFINITE", "명시적 4xx 거부는 안전한 실패여야 한다");
assert(submission.classifySubmissionFailure({ status: 503, code: "ERROR" }) === "UNCERTAIN", "5xx 응답은 저장 여부 reconciliation이 필요하다");
assert(submission.classifySubmissionFailure({ status: 503, code: "OUTAGE_MUTATION_UNCERTAIN" }) === "UNCERTAIN", "장애 mutation 불명은 재전송하면 안 된다");
const reconciled = submission.findReconciledAnswer([{
  questionId: 91,
  answerId: 501,
  answerText: "답변",
  audioUrl: "/api/file/71/content",
  videoUrl: null,
}], { questionId: 91, answerText: "답변", mediaKind: "AUDIO", fileId: 71, contentUrl: "/api/file/71/content" });
assert(reconciled?.answerId === 501, "응답 유실 후 review의 본문+원본으로 저장 성공을 판정해야 한다");
assert(submission.findReconciledAnswer([reconciled], { questionId: 91, answerText: "다른 답변" }) === null, "다른 답변을 이번 제출 성공으로 오인하면 안 된다");
assert(submission.SUBMISSION_RECONCILE_DELAYS_MS.length >= 3, "불명 제출은 단일 review 조회로 미저장을 단정하면 안 된다");
assert(submission.concludeMissingSubmissionReconciliation(3, 3, false) === "NOT_SAVED", "모든 review가 정상이고 모두 미저장일 때만 안전한 재시도를 허용해야 한다");
assert(submission.concludeMissingSubmissionReconciliation(2, 3, true) === "UNKNOWN", "조회 실패나 outage가 한 번이라도 섞이면 결과 불명으로 격리해야 한다");

const sessionListState = await importTypeScriptModule("src/features/interview/lib/sessionListState.ts");
const completedSession = sessionListState.getInterviewSessionListState({
  endedAt: "2026-07-12T10:00:00",
  totalQuestions: 6,
  answeredQuestions: 6,
  finished: true,
});
assert(completedSession.kind === "DONE" && completedSession.progress === 100, "모든 질문에 답한 세션만 완료여야 한다");
const partialReport = sessionListState.getInterviewSessionListState({
  endedAt: "2026-07-12T10:00:00",
  totalQuestions: 6,
  answeredQuestions: 1,
  finished: false,
});
assert(partialReport.kind === "REPORTED", "종료 시각이 있어도 미완료면 완료로 표시하면 안 된다");
assert(partialReport.progress === 17 && partialReport.label.includes("1/6"), "미완료 리포트는 실제 답변 진행률을 표시해야 한다");
const runningSession = sessionListState.getInterviewSessionListState({
  endedAt: null,
  totalQuestions: 6,
  answeredQuestions: 2,
  finished: false,
});
assert(runningSession.kind === "RUNNING" && runningSession.progress === 33, "진행 중 세션도 목록 집계 진행률을 사용해야 한다");

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
assert(mobileThreadSource.includes("protectedFileIdsRef"), "제출 중/결과 불명 원본은 pending cleanup과 격리해야 한다");
assert(mobileThreadSource.includes("activeUploadAbortRef.current?.abort()"), "앱 잠금·세션 전환·이탈은 진행 중 원본 업로드를 중단해야 한다");
assert(mobileThreadSource.includes("SUBMISSION_RECONCILE_DELAYS_MS"), "응답 유실은 여러 번 review reconciliation해야 한다");
assert(mobileThreadSource.includes("useLayoutEffect"), "세션 param 전환은 passive effect 전에 transient 상태를 닫아야 한다");
const voiceOverlaySource = await readFile(resolve("src/features/interview/components/mobile/ImmersiveVoiceOverlay.tsx"), "utf8");
const avatarOverlaySource = await readFile(resolve("src/features/interview/components/mobile/ImmersiveAvatarOverlay.tsx"), "utf8");
assert(voiceOverlaySource.includes("audioBlob: blob"), "음성 오버레이는 제출용 원본 Blob을 부모에 인계해야 한다");
assert(avatarOverlaySource.includes("videoBlob: blob"), "영상 오버레이는 제출용 원본 Blob을 부모에 인계해야 한다");
assert(avatarOverlaySource.includes("captureAttempt !== captureAttemptRef.current"), "늦게 반환된 이전 카메라 스트림은 현재 flip 시도를 덮으면 안 된다");
assert(avatarOverlaySource.includes("videoBitsPerSecond: MOBILE_INTERVIEW_VIDEO_BITS_PER_SECOND"), "모바일 영상 녹화 bitrate를 recorder에 적용해야 한다");
assert(voiceOverlaySource.includes("audioBitsPerSecond: MOBILE_INTERVIEW_AUDIO_BITS_PER_SECOND"), "모바일 음성 녹화 bitrate를 recorder에 적용해야 한다");
assert(voiceOverlaySource.includes("controller.signal") && avatarOverlaySource.includes("controller.signal"), "앱 잠금/닫기 후 STT·채점 요청을 중단할 수 있어야 한다");
assert(voiceOverlaySource.includes("MOBILE_INTERVIEW_AUDIO_MAX_SECONDS"), "모바일 음성 녹음은 base64 변환 전 최대 길이를 제한해야 한다");
assert(avatarOverlaySource.includes("MOBILE_INTERVIEW_VIDEO_MAX_SECONDS"), "모바일 영상 녹화는 업로드 안전 최대 길이를 제한해야 한다");
assert(voiceOverlaySource.includes("chunksRef.current = []") && avatarOverlaySource.includes("chunksRef.current = []"), "닫기·앱 잠금 시 민감 녹음 chunk를 비워야 한다");
const prepromptSource = await readFile(resolve("src/features/interview/components/mobile/PermissionPreprompt.tsx"), "utf8");
assert(!prepromptSource.includes("원본은 즉시 폐기"), "권한 안내가 실제 원본 보관 동작과 충돌하면 안 된다");
assert(prepromptSource.includes("삭제 후에는 원본 기반 재분석이 불가능"), "원본 삭제의 재분석 제한을 동의 전에 알려야 한다");
assert(prepromptSource.includes("preprompt.v2"), "원본 보관 정책 변경 후에는 기존 즉시 폐기 동의를 재사용하면 안 된다");
assert(mobileThreadSource.includes("audioBlob?.size") && mobileThreadSource.includes("videoBlob?.size"), "STT가 비어도 캡처 원본은 pending 업로드해야 한다");
assert(mobileThreadSource.indexOf("setPendingMedia({ questionId, kind, file") < mobileThreadSource.indexOf("cleanupPendingFile(previous.file.id)"), "새 원본 저장이 확정되기 전에 이전 pending 원본을 삭제하면 안 된다");
const remoteMediaSource = await readFile(resolve("src/features/interview/components/RemoteMicConnectCard.tsx"), "utf8");
assert(remoteMediaSource.includes("useAppLockCleanup"), "원격 WebRTC 수신 연결도 앱 잠금 시 종료해야 한다");

const viteServer = await createServer({ server: { middlewareMode: true }, appType: "custom" });
try {
  const mock = await viteServer.ssrLoadModule("/src/app/lib/mock/index.ts");
  const audioForm = new FormData();
  audioForm.append("file", new Blob(["voice"], { type: "audio/webm" }), "voice.webm");
  audioForm.append("kind", "AUDIO");
  audioForm.append("refType", "INTERVIEW_ANSWER");
  const videoForm = new FormData();
  videoForm.append("file", new Blob(["video"], { type: "video/webm" }), "video.webm");
  videoForm.append("kind", "VIDEO");
  videoForm.append("refType", "INTERVIEW_ANSWER");
  videoForm.append("refId", "91022");

  const audioAsset = await mock.resolveMock("/file/upload", { method: "POST", body: audioForm });
  const videoAsset = await mock.resolveMock("/file/upload", { method: "POST", body: videoForm });
  assert(audioAsset.kind === "AUDIO" && audioAsset.refType === "INTERVIEW_ANSWER", "mock 업로드가 음성 kind/refType을 보존해야 한다");
  assert(videoAsset.kind === "VIDEO" && videoAsset.refType === "INTERVIEW_ANSWER" && videoAsset.refId === 91022, "mock 업로드가 영상 kind/refType/refId를 보존해야 한다");
  assert(audioAsset.id !== videoAsset.id, "mock 업로드 파일 ID는 요청마다 고유해야 한다");
  assert(audioAsset.contentUrl === `/api/file/${audioAsset.id}/content`, "mock 업로드는 재생 가능한 형식의 contentUrl을 반환해야 한다");
  assert(videoAsset.contentUrl === `/api/file/${videoAsset.id}/content`, "mock 영상 업로드도 비어 있지 않은 contentUrl을 반환해야 한다");

  const reviewBeforeDelete = await mock.resolveMock("/interview/sessions/8002/review", { method: "GET" });
  assert(reviewBeforeDelete.items.find((item) => item.answerId === 91021)?.audioUrl, "삭제 테스트 fixture에 음성 원본이 있어야 한다");
  await mock.resolveMock("/interview/answers/91021/media/AUDIO", { method: "DELETE" });
  const reviewAfterDelete = await mock.resolveMock("/interview/sessions/8002/review", { method: "GET" });
  const deletedAnswer = reviewAfterDelete.items.find((item) => item.answerId === 91021);
  assert(deletedAnswer?.audioUrl === null, "mock 원본 삭제는 이후 review 응답에 유지되어야 한다");
  assert(deletedAnswer?.answerText && deletedAnswer.score === 84, "mock 원본 삭제가 답변과 채점을 지우면 안 된다");

  const consentValues = new Map();
  globalThis.localStorage = {
    get length() { return consentValues.size; },
    clear: () => consentValues.clear(),
    getItem: (key) => consentValues.get(key) ?? null,
    key: (index) => [...consentValues.keys()][index] ?? null,
    removeItem: (key) => consentValues.delete(key),
    setItem: (key, value) => consentValues.set(key, String(value)),
  };
  const permission = await viteServer.ssrLoadModule("/src/features/interview/components/mobile/PermissionPreprompt.tsx");
  permission.markPrepromptAccepted("voice", " User-101@Example.com ");
  assert(permission.isPrepromptAccepted("voice", "user-101@example.com"), "동일 계정의 정규화된 scope는 동의를 읽어야 한다");
  assert(!permission.isPrepromptAccepted("voice", "user-102@example.com"), "다른 계정이 기기 내 동의를 공유하면 안 된다");
  assert(!permission.isPrepromptAccepted("avatar", "user-101@example.com"), "음성과 영상 권한 동의를 서로 공유하면 안 된다");
  permission.markPrepromptAccepted("voice", "   ");
  assert(consentValues.size === 1, "빈 계정 scope로 device-global 동의 키를 만들면 안 된다");
  assert(!consentValues.has("careertuner.perm.preprompt.v2.voice"), "기존 무스코프 v2 동의 키를 재사용하면 안 된다");
} finally {
  delete globalThis.localStorage;
  await viteServer.close();
}

console.log("PASS mobile platform tests");
