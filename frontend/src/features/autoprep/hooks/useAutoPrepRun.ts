import { useCallback, useEffect, useRef, useState } from "react";

import { cancelAutoPrepRun, runStream } from "../api/autoPrepApi";
import type { AutoPrepRequest, PrepEvent, PrepPlan, PrepStepResult } from "../types/autoPrep";

export type PartUiStatus = "pending" | "running" | "done" | "skipped" | "failed";

export interface PartState {
  key: string;
  status: PartUiStatus;
  substeps: { name: string; desc: string }[];
  result?: PrepStepResult;
}

export interface AutoPrepRunState {
  running: boolean;
  plan: PrepPlan | null;
  parts: PartState[];
  message: string | null;
  error: string | null;
}

const INITIAL: AutoPrepRunState = {
  running: false,
  plan: null,
  parts: [],
  message: null,
  error: null,
};

function createRunId(): string {
  const randomUuid = globalThis.crypto?.randomUUID?.();
  return randomUuid
    ? randomUuid.replaceAll("-", "_")
    : `autoprep_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 12)}`;
}

/**
 * 무이벤트 워치독 — 마지막 SSE 이벤트 후 이 시간 동안 침묵이면 스트림 사망으로 판정하고 강제 종료.
 * 산정 근거: 서버 SseEmitter 총 타임아웃이 300초(AutoPrepOrchestrator.SSE_TIMEOUT_MS=300_000L)라
 * 정상 스트림은 늦어도 300초에 서버가 닫는다(닫히면 reader 가 done 을 받아 이 타이머와 무관하게 종료).
 * 그 이상 "무이벤트"는 종료 신호 자체가 클라이언트에 도달하지 못한 것(무소음 네트워크 단절)뿐이므로,
 * 서버 상한 × 1.1 = 330초를 클라 판정선으로 둔다(서버보다 작으면 정상 진행을 오판할 수 있음).
 */
export const STREAM_SILENCE_TIMEOUT_MS = 330_000;

export interface AutoPrepStreamLifecycleDependencies {
  stream?: typeof runStream;
  cancel?: typeof cancelAutoPrepRun;
}

/** 브라우저 fetch 계열 원문("Failed to fetch" 등 영문)은 그대로 노출하지 않는다 — 한글 없는 메시지는 일반 안내로. */
function toFriendlyError(err: Error): string {
  const msg = err.message || "";
  return /[가-힣]/.test(msg) ? msg : "네트워크 문제로 실행이 중단됐어요. 잠시 후 다시 시도해 주세요.";
}

/**
 * 스트림 생애주기 코어(React 무의존) — 구독·무이벤트 워치독·종료 시 미결 파트 정리를 한 곳에서.
 * 훅은 이 함수를 setState 어댑터(apply)로 감싸기만 한다. React 밖(노드 검증 하네스)에서도 그대로 돈다.
 * apply 는 "이 실행이 여전히 최신일 때만" 상태를 반영해야 한다(훅에서는 seq 가드로 보장).
 * silenceTimeoutMs 는 검증 하네스 주입용 — 프로덕션 호출은 기본값을 쓴다.
 */
export async function driveRunStream(
  req: AutoPrepRequest,
  apply: (updater: (prev: AutoPrepRunState) => AutoPrepRunState) => void,
  ac: AbortController,
  silenceTimeoutMs: number = STREAM_SILENCE_TIMEOUT_MS,
  dependencies: AutoPrepStreamLifecycleDependencies = {},
): Promise<void> {
  const stream = dependencies.stream ?? runStream;
  const cancelServerRun = dependencies.cancel ?? cancelAutoPrepRun;
  // 워치독: 이벤트가 올 때마다 재장전. 발화하면 abort 로 reader 를 깨워 아래 catch 에서 정리.
  let silenceFired = false;
  let terminalReceived = false;
  let serverCloseRequested = false;
  let watchdog: ReturnType<typeof setTimeout> | undefined;
  const armWatchdog = () => {
    clearTimeout(watchdog);
    watchdog = setTimeout(() => {
      silenceFired = true;
      ac.abort();
    }, silenceTimeoutMs);
  };
  armWatchdog();

  const closeOrphanedServerRun = async () => {
    if (terminalReceived || serverCloseRequested || !req.runId) return;
    serverCloseRequested = true;
    await cancelServerRun(req.runId).catch(() => undefined);
  };

  try {
    await stream(
      req,
      (e) => {
        if (e.type === "done" || e.type === "error") {
          terminalReceived = true;
          clearTimeout(watchdog);
        } else {
          armWatchdog();
        }
        apply((prev) => reduce(prev, e));
      },
      ac.signal,
    );
    // non-abort EOF/network error는 autoPrepApi.runStream이 서버 취소 접수까지 마친 뒤 반환한다.
    // 스트림 정상 종료 — 이후 이벤트는 올 수 없으므로 미결 파트는 여기서 실패로 확정(림보 차단).
    apply((prev) => settleDanglingParts(prev, null));
  } catch (err) {
    // 사용자 cancel/reset/retry는 해당 액션과 api abort listener가 취소한다. watchdog abort만 여기서 await 보강한다.
    if (!terminalReceived && silenceFired) await closeOrphanedServerRun();
    if (silenceFired) {
      apply((prev) =>
        settleDanglingParts(prev, "응답이 오래 없어 분석을 중단했어요. 다시 시도해 주세요."));
    } else if (!ac.signal.aborted) {
      apply((prev) => settleDanglingParts(prev, toFriendlyError(err as Error)));
    }
    // 사용자 취소(cancel/reset/새 start 의 abort)는 상태를 손대지 않는다 — 각 액션이 직접 정리.
  } finally {
    clearTimeout(watchdog);
    apply((prev) => ({ ...prev, running: false }));
  }
}

/** SSE 실행을 구독해 6파트 진행 상태를 누적하는 훅 — 코어는 driveRunStream, 여기는 React 어댑터. */
export function useAutoPrepRun() {
  const [state, setState] = useState<AutoPrepRunState>(INITIAL);
  const abortRef = useRef<AbortController | null>(null);
  const runIdRef = useRef<string | null>(null);
  // 실행 세대 — 재시도(start 재호출)로 이전 스트림을 abort 했을 때, 이전 호출의 늦은 정리가
  // 새 실행의 상태(running 등)를 덮어쓰지 않게 한다.
  const runSeqRef = useRef(0);

  // 패널 자체가 라우트 전환으로 unmount되면 모달 close 콜백을 거치지 않을 수 있다.
  // 진행 중 SSE를 끊고 세대를 폐기해 숨은 AI 실행과 unmount 후 상태 반영을 막는다.
  useEffect(() => () => {
    runSeqRef.current += 1;
    const runId = runIdRef.current;
    runIdRef.current = null;
    if (runId) void cancelAutoPrepRun(runId, true).catch(() => undefined);
    abortRef.current?.abort();
    abortRef.current = null;
  }, []);

  const start = useCallback(async (req: AutoPrepRequest) => {
    const seq = ++runSeqRef.current;
    const previousRunId = runIdRef.current;
    runIdRef.current = null;
    abortRef.current?.abort();
    abortRef.current = null;
    // 재시도는 이전 실행의 서버 취소 접수 뒤 시작해 같은 유료 파트가 동시에 새로 출발하는 창을 줄인다.
    if (previousRunId) {
      await cancelAutoPrepRun(previousRunId).catch(() => undefined);
      if (runSeqRef.current !== seq) return;
    }
    const ac = new AbortController();
    abortRef.current = ac;
    const executionRequest: AutoPrepRequest = { ...req, runId: createRunId() };
    runIdRef.current = executionRequest.runId as string;
    setState({ ...INITIAL, running: true });
    try {
      await driveRunStream(
        executionRequest,
        (updater) => {
          if (runSeqRef.current === seq) setState(updater);
        },
        ac,
      );
    } finally {
      if (runSeqRef.current === seq && abortRef.current === ac) {
        abortRef.current = null;
        runIdRef.current = null;
      }
    }
  }, []);

  const cancel = useCallback(() => {
    const runId = runIdRef.current;
    runIdRef.current = null;
    if (runId) void cancelAutoPrepRun(runId, true).catch(() => undefined);
    abortRef.current?.abort();
    abortRef.current = null;
    setState((prev) => ({ ...prev, running: false }));
  }, []);

  const reset = useCallback(() => {
    const runId = runIdRef.current;
    runIdRef.current = null;
    if (runId) void cancelAutoPrepRun(runId, true).catch(() => undefined);
    abortRef.current?.abort();
    abortRef.current = null;
    setState(INITIAL);
  }, []);

  return { ...state, start, cancel, reset };
}

/**
 * 스트림 종료 시점 정리 — pending/running 파트는 더 이상 settle 될 수 없으므로 failed 로 확정한다.
 * error 이벤트/done 없이 죽은 비정상 종료(파트 미결 or 아무것도 못 받음)면 에러 메시지도 채워
 * 소비자(위젯 배너·가이드 fit 오류)가 침묵하지 않게 한다. 기존 에러는 보존(먼저 온 원인이 우선).
 */
function settleDanglingParts(s: AutoPrepRunState, fallbackError: string | null): AutoPrepRunState {
  const dangling = s.parts.some((p) => p.status === "pending" || p.status === "running");
  const emptyDeath = s.parts.length === 0 && s.message == null; // plan 도 못 받고 죽음(F-09)
  if (!dangling && !emptyDeath && fallbackError == null) return s;
  const error =
    s.error ??
    fallbackError ??
    (dangling || emptyDeath ? "분석 연결이 중간에 끊겼어요. 다시 시도해 주세요." : null);
  return {
    ...s,
    error,
    parts: dangling
      ? s.parts.map((p) =>
          p.status === "pending" || p.status === "running" ? { ...p, status: "failed" as const } : p)
      : s.parts,
  };
}

function reduce(s: AutoPrepRunState, e: PrepEvent): AutoPrepRunState {
  switch (e.type) {
    case "plan":
      return {
        ...s,
        plan: e.plan,
        parts: e.plan.steps.map((key) => ({ key, status: "pending", substeps: [] })),
      };
    case "part-start":
      return { ...s, parts: s.parts.map((p) => (p.key === e.key ? { ...p, status: "running" } : p)) };
    case "substep":
      return {
        ...s,
        parts: s.parts.map((p) =>
          p.key === e.key ? { ...p, substeps: [...p.substeps, { name: e.name, desc: e.desc }] } : p,
        ),
      };
    case "part-done": {
      const status: PartUiStatus =
        e.result.status === "DONE" ? "done" : e.result.status === "SKIPPED" ? "skipped" : "failed";
      return {
        ...s,
        parts: s.parts.map((p) => (p.key === e.result.key ? { ...p, status, result: e.result } : p)),
      };
    }
    case "done":
      return { ...s, message: e.message, running: false };
    case "error":
      return { ...s, error: e.message, running: false };
    default:
      return s;
  }
}
