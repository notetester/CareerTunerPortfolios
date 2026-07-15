import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createServer } from "vite";

const [apiSource, authSource, registrySource, launcherSource, chatSource, runHookSource, supportHookSource, mockSource,
  onboardingApiSource, onboardingGuideSource] = await Promise.all([
  readFile(new URL("../src/features/autoprep/api/autoPrepApi.ts", import.meta.url), "utf8"),
  readFile(new URL("../src/app/auth/AuthContext.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/app/lib/pendingAutoPrepFiles.ts", import.meta.url), "utf8"),
  readFile(new URL("../src/features/autoprep/components/AutoPrepLauncher.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/features/autoprep/components/AutoPrepChatModal.tsx", import.meta.url), "utf8"),
  readFile(new URL("../src/features/autoprep/hooks/useAutoPrepRun.ts", import.meta.url), "utf8"),
  readFile(new URL("../src/features/support/hooks/useChatbot.ts", import.meta.url), "utf8"),
  readFile(new URL("../src/app/lib/mock/domains/autoprep.ts", import.meta.url), "utf8"),
  readFile(new URL("../src/features/support/api/onboardingApi.ts", import.meta.url), "utf8"),
  readFile(new URL("../src/features/support/hooks/useOnboardingGuide.ts", import.meta.url), "utf8"),
]);

assert.match(apiSource, /fd\.append\("refType", "AUTO_PREP_PENDING"\)/);
assert.match(apiSource, /trackPendingAutoPrepUpload\(/);
assert.doesNotMatch(apiSource, /if \(completed\)[\s\S]*?discardPendingAutoPrepFiles/,
  "part 실패를 포함한 done에서 retry 입력 파일을 지우면 안 된다");
assert.match(apiSource, /\/auto-prep\/run\/cancel/);
assert.match(apiSource, /signal\.addEventListener\("abort"[\s\S]*?cancelAutoPrepRun/,
  "fetch abort와 서버 협력적 취소를 함께 전송해야 한다");
assert.match(apiSource, /terminalReceived = true[\s\S]*?reader\.cancel\(\)/,
  "done/error terminal은 reader만 닫고 cancel tombstone을 만들면 안 된다");
assert.match(apiSource, /clean EOF[\s\S]*?cancelOrphanedAutoPrepRun\(req, terminalReceived, signal\)/,
  "terminal 없는 clean EOF는 현재 runId 서버 취소를 await해야 한다");
assert.match(apiSource, /streamOpened \|\| isNetworkOutageError\(error\)[\s\S]*?cancelOrphanedAutoPrepRun/,
  "terminal 없는 stream/network 오류도 현재 runId 서버 취소를 await해야 한다");
assert.match(apiSource, /getJobPostingExtraction/);
assert.match(apiSource, /shouldUseMockData\(\)/);
assert.match(apiSource, /const envelope = \(await res\.json\(\)\.catch\(\(\) => null\)\)/,
  "SSE 시작 전 400 JSON envelope를 읽어야 한다");
assert.match(apiSource, /new ApiError\([\s\S]*?envelope\?\.message[\s\S]*?envelope\?\.code/,
  "첨부 한도 초과 같은 서버 메시지를 실행 UI에 전달해야 한다");
assert.match(authSource, /discardPendingAutoPrepFiles\(\)/);
assert.match(authSource, /forgetPendingAutoPrepFiles\(\)/);
assert.match(registrySource, /PendingCollaborationFileRegistry/);
assert.match(launcherSource, /key: createFileItemKey\(\)/);
assert.match(launcherSource, /f\.key === item\.key/);
assert.doesNotMatch(launcherSource, /f === item/);
assert.match(launcherSource, /createJobPostingCaseFromFile\([\s\S]*?first\.id as number,[\s\S]*?resumeIds/,
  "binary 공고와 자소서 합산 한도는 case 생성 전 서버 엔드포인트에 함께 전달해야 한다");
assert.match(chatSource, /generationRef/);
assert.match(chatSource, /getJobPostingExtraction\(caseId, controller\.signal\)/);
assert.match(chatSource, /discardPendingAutoPrepFiles\(req\.jobPostingFileIds\)/);
assert.match(chatSource, /setIntakeRetryRequest\(req\)/);
assert.match(chatSource, /같은 요청으로 다시 시도/);
assert.match(chatSource, /if \(!open\) \{[\s\S]*?disposeActiveRequest\(true\)/,
  "부모가 open=false로 닫아도 pending cleanup과 retry 무효화를 수행해야 한다");
assert.match(chatSource, /function closeModal\(\) \{[\s\S]*?disposeActiveRequest\(true\)/,
  "닫기 버튼도 pending cleanup과 retry 무효화를 수행해야 한다");
assert.match(chatSource,
  /const cycle = \+\+mountCycleRef\.current;[\s\S]*?setTimeout\(\(\) => \{[\s\S]*?mountCycleRef\.current !== cycle[\s\S]*?disposeActiveRequest\(true\)/,
  "라우트 unmount 정리는 mount cycle 뒤로 지연해 StrictMode 가짜 cleanup에서 pending 파일을 삭제하면 안 된다");
assert.match(chatSource,
  /lastRunReqRef\.current = null;\s*currentIntakeRequestRef\.current = null;\s*invalidateIntake\(\)/,
  "새 인테이크는 이전 실행 ref가 자기 pending cleanup을 가로막기 전에 비워야 한다");
assert.match(chatSource, /activeIdRef\.current !== r\.id[\s\S]*?disposeActiveRequest\(\)/,
  "과거 이력을 재실행할 때 기존 live session의 pending lifecycle을 먼저 폐기해야 한다");
assert.match(chatSource, /cleanupRequestLifecycle\(record\.lastRequest, record\.id\)/,
  "stale attachment 이력은 retry 참조와 pending 파일을 함께 정리해야 한다");
const chatAttachSource = chatSource.slice(
  chatSource.indexOf("async function attachCoverLetter"),
  chatSource.indexOf("async function begin"),
);
assert.match(chatAttachSource, /attachmentFileIds: \[uploaded\.id\]/,
  "FREE 플랜 후기 자소서는 이전 unreadable ID에 append하지 않고 최신 1개로 교체해야 한다");
assert.doesNotMatch(chatAttachSource, /attachmentFileIds:\s*\[\.\.\./,
  "후기 자소서 교체가 기존 ID를 남겨 합산 한도를 2개로 만들면 안 된다");
assert.match(chatAttachSource, /updatePrepSession\(activeIdRef\.current, \{ lastRequest: next \}\)/,
  "ChatModal persisted retry도 최신 자소서 1개로 즉시 교체해야 한다");
assert.match(chatAttachSource, /discardPendingAutoPrepFiles\(supersededIds\)/,
  "교체된 이전 pending 자소서는 새 fileId를 제외하고 회수해야 한다");
assert.match(chatAttachSource,
  /const generation = generationRef\.current;\s*const sessionId = activeIdRef\.current;\s*const lifecycleVersion = activeLifecycleVersionRef\.current;\s*const uploaded = await uploadAttachment\(file\)/,
  "upload await 전에 실행 세대·활성 세션·lifecycle을 고정해야 한다");
assert.match(chatAttachSource,
  /!mountedRef\.current[\s\S]*?!openRef\.current[\s\S]*?generationRef\.current !== generation[\s\S]*?activeIdRef\.current !== sessionId[\s\S]*?activeLifecycleDisposedRef\.current[\s\S]*?activeLifecycleVersionRef\.current !== lifecycleVersion[\s\S]*?await discardPendingAutoPrepFiles\(\[uploaded\.id\]\)[\s\S]*?return/,
  "닫기·새 준비·unmount 뒤 완료된 upload는 새 파일만 회수하고 stale run을 시작하면 안 된다");
assert.doesNotMatch(chatAttachSource, /activeLifecycleDisposedRef\.current = false/,
  "늦게 완료된 upload가 폐기된 lifecycle을 되살리면 안 된다");
const supportAttachSource = supportHookSource.slice(
  supportHookSource.indexOf("const attachCoverLetter = useCallback"),
  supportHookSource.indexOf("/* ── 모드 이탈"),
);
assert.match(supportAttachSource, /attachmentFileIds: \[uploaded\.id\]/,
  "support 후기 자소서도 최신 1개 replacement semantics를 사용해야 한다");
assert.doesNotMatch(supportAttachSource, /attachmentFileIds:\s*\[\.\.\./,
  "support 후기 자소서가 기존 unreadable ID에 append되면 안 된다");
assert.match(supportAttachSource, /discardPendingAutoPrepFiles\(supersededIds\)/,
  "support에서 교체된 이전 pending 자소서도 회수해야 한다");
assert.match(chatSource, /updatePrepSession\(activeIdRef\.current, \{ lastRequest: nextReq \}\)/,
  "추출 완료 후 삭제한 posting fileId를 persisted retry에서도 제거해야 한다");
assert.match(chatSource, /error instanceof ApiError && \/\[가-힣\]\//,
  "intake는 서버가 보낸 한글 ApiError만 사용자에게 보존해야 한다");
assert.match(chatSource, /catch \(error\)[\s\S]*?text: intakeErrorMessage\(error\)/,
  "intake 400 메시지를 일반 오류로 덮어쓰면 안 된다");
assert.ok((chatSource.match(/if \(controller\.signal\.aborted\) return;/g) ?? []).length >= 2,
  "대체된 intake와 extraction GET의 abort를 사용자 오류/재폴링으로 처리하면 안 된다");
assert.match(runHookSource, /useEffect\(\(\) => \(\) => \{[\s\S]*?runSeqRef\.current \+= 1;[\s\S]*?abortRef\.current\?\.abort\(\)/,
  "라우트 전환 unmount도 진행 중 AutoPrep SSE를 중단해야 한다");
assert.match(runHookSource, /runId: createRunId\(\)/);
assert.match(runHookSource, /await cancelAutoPrepRun\(previousRunId\)[\s\S]*?runSeqRef\.current !== seq/,
  "재시도는 이전 runId의 서버 취소 접수를 기다리고 세대가 최신일 때만 새 실행해야 한다");
assert.match(mockSource, /pattern: \/\^\\\/auto-prep\\\/intake\$\//);
assert.match(mockSource, /pattern: \/\^\\\/auto-prep\\\/job-posting-case\\\/upload\$\//);
assert.match(
  onboardingApiSource,
  /AUTO_PREP_PENDING_REF_TYPE\s*=\s*"AUTO_PREP_PENDING"[\s\S]*?fd\.append\("refType", refType\)/,
  "온보딩 자소서 업로드 API도 AutoPrep TTL 정리 표식을 전송해야 한다",
);
assert.match(
  onboardingGuideSource,
  /slot === "resume" \? PROFILE_IMPORT_PENDING_REF_TYPE : AUTO_PREP_PENDING_REF_TYPE/,
  "온보딩 자소서는 AUTO_PREP_PENDING TTL 정리 경로에 등록해야 한다",
);
assert.match(
  onboardingGuideSource,
  /uploadDocument\([\s\S]*?file,[\s\S]*?"ATTACHMENT",[\s\S]*?AUTO_PREP_PENDING_REF_TYPE,[\s\S]*?\)/,
  "분석 결과 카드에서 뒤늦게 첨부한 자소서도 같은 pending 계약을 사용해야 한다",
);

// AutoPrep도 전역 file mock을 실제 레지스트리 순서로 통과해야 한다(도메인별 중복 route 금지).
const viteServer = await createServer({ server: { middlewareMode: true }, appType: "custom" });
try {
  const mock = await viteServer.ssrLoadModule("/src/app/lib/mock/index.ts");
  const autoPrepApi = await viteServer.ssrLoadModule("/src/features/autoprep/api/autoPrepApi.ts");
  const autoPrepRun = await viteServer.ssrLoadModule("/src/features/autoprep/hooks/useAutoPrepRun.ts");
  const uploadBody = new FormData();
  uploadBody.append("file", new Blob(["자소서"], { type: "text/plain" }), "cover.txt");
  uploadBody.append("kind", "ATTACHMENT");
  uploadBody.append("refType", "AUTO_PREP_PENDING");
  const uploaded = await mock.resolveMock("/file/upload", { method: "POST", body: uploadBody });
  assert.equal(typeof uploaded.id, "number");
  assert.equal(uploaded.refType, "AUTO_PREP_PENDING");
  assert.equal(await mock.resolveMock(`/file/${uploaded.id}`, { method: "DELETE" }), null);

  const binaryBody = new FormData();
  binaryBody.append("pendingFileId", String(uploaded.id));
  const firstCase = await mock.resolveMock(
    "/auto-prep/job-posting-case/upload",
    { method: "POST", body: binaryBody },
  );
  const retryCase = await mock.resolveMock(
    "/auto-prep/job-posting-case/upload",
    { method: "POST", body: binaryBody },
  );
  assert.equal(firstCase.applicationCaseId, retryCase.applicationCaseId,
    "정적 데모 binary 공고도 같은 pendingFileId 재시도에 같은 지원 건을 반환해야 한다");

  const cancelledRunIds = [];
  const cancel = async (runId) => { cancelledRunIds.push(runId); };
  assert.equal(await autoPrepApi.cancelOrphanedAutoPrepRun(
    { runId: "run_eof" }, false, undefined, cancel,
  ), true);
  assert.deepEqual(cancelledRunIds, ["run_eof"],
    "terminal 없는 EOF/network 종료는 취소 요청 완료를 기다려야 한다");
  assert.equal(await autoPrepApi.cancelOrphanedAutoPrepRun(
    { runId: "run_done" }, true, undefined, cancel,
  ), false);
  assert.equal(await autoPrepApi.cancelOrphanedAutoPrepRun(
    { runId: "run_abort" }, false, AbortSignal.abort(), cancel,
  ), false);
  assert.deepEqual(cancelledRunIds, ["run_eof"],
    "정상 terminal 및 명시 abort에는 중복 cancel/early tombstone을 만들면 안 된다");

  const initialRunState = () => ({
    running: true, plan: null, parts: [], message: null, error: null,
  });
  let terminalState = initialRunState();
  const terminalCancels = [];
  await autoPrepRun.driveRunStream(
    { runId: "run_terminal" },
    (updater) => { terminalState = updater(terminalState); },
    new AbortController(),
    20,
    {
      stream: async (_request, on) => { on({ type: "done", message: "완료" }); },
      cancel: async (runId) => { terminalCancels.push(runId); },
    },
  );
  assert.deepEqual(terminalCancels, [],
    "useAutoPrepRun도 정상 done 뒤 watchdog/cancel을 남기면 안 된다");

  let timeoutState = initialRunState();
  const timeoutCancels = [];
  await autoPrepRun.driveRunStream(
    { runId: "run_timeout" },
    (updater) => { timeoutState = updater(timeoutState); },
    new AbortController(),
    5,
    {
      stream: async (_request, _on, signal) => new Promise((_resolve, reject) => {
        signal.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")), { once: true });
      }),
      cancel: async (runId) => { timeoutCancels.push(runId); },
    },
  );
  assert.deepEqual(timeoutCancels, ["run_timeout"],
    "watchdog의 비명시 abort는 서버 취소 접수를 await해야 한다");
  assert.match(timeoutState.error ?? "", /응답이 오래 없어/);
} finally {
  await viteServer.close();
}

console.log("pending AutoPrep attachment lifecycle contract: ok");
