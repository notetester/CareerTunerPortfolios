import {
  api,
  apiRaw,
  ApiError,
  createOutageMutationUncertainError,
  type ApiEnvelope,
} from "@/app/lib/api";
import {
  activateOutageFallbackIfConfirmed,
  isNetworkOutageError,
  isOutageStatus,
  shouldUseMockData,
} from "@/app/lib/outageFallback";
import type {
  AutoPrepExtractionStatus,
  AutoPrepIntakeResponse,
  AutoPrepRequest,
  PrepAttachedFile,
  PrepEvent,
} from "../types/autoPrep";
import {
  trackPendingAutoPrepUpload,
} from "@/app/lib/pendingAutoPrepFiles";

// SSE 는 ApiResponse envelope 를 안 타므로 api() 래퍼를 못 쓴다 → fetch 직접 + 토큰 수동 첨부.
// 베이스 URL 은 apiBase() 단일 소스를 사용한다(런타임 오버라이드 반영).

/** 인테이크: 한 줄 요청 해석 + 슬롯 확인(미리보기). ready=true 면 그대로 run. */
export function intake(req: AutoPrepRequest, signal?: AbortSignal) {
  return api<AutoPrepIntakeResponse>("/auto-prep/intake", {
    method: "POST",
    body: JSON.stringify(req),
    signal,
  });
}

/** 공고 추출 상태만 조회한다. EXTRACTING 대기 중 인테이크 LLM을 반복 호출하지 않는다. */
export function getJobPostingExtraction(applicationCaseId: number, signal?: AbortSignal) {
  return api<AutoPrepExtractionStatus | null>(`/application-cases/${applicationCaseId}/job-posting/extraction`, {
    method: "GET",
    signal,
  });
}

/** 첨부 파일 업로드(kind=ATTACHMENT) → fileId. 플랜 게이팅은 실행 시 백엔드가 적용. */
export function uploadAttachment(file: File) {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("kind", "ATTACHMENT");
  fd.append("refType", "AUTO_PREP_PENDING");
  return trackPendingAutoPrepUpload(api<PrepAttachedFile>("/file/upload", { method: "POST", body: fd }));
}

/**
 * 공고 파일(이미지/PDF) → 지원 건 생성(비동기 OCR·회사·직무 추출 큐잉) → 생성된 지원 건 id.
 * 이미지/스캔 공고는 텍스트 첨부 추출로 읽을 수 없으므로 AutoPrep 전용 경계에서 자소서와 합산 한도를
 * 먼저 검증하고 B 지원 건 생성 서비스를 호출한다. pendingFileId는 응답 유실 재전송의 멱등키다.
 */
export async function createJobPostingCaseFromFile(
  file: File,
  sourceType: "PDF" | "IMAGE",
  pendingFileId: number,
  attachmentFileIds: number[],
): Promise<number> {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("sourceType", sourceType);
  fd.append("pendingFileId", String(pendingFileId));
  attachmentFileIds.forEach((fileId) => fd.append("attachmentFileIds", String(fileId)));
  const res = await api<{ applicationCaseId: number }>(
    "/auto-prep/job-posting-case/upload",
    { method: "POST", body: fd },
  );
  return res.applicationCaseId;
}

/** 브라우저 fetch abort와 별도로 서버의 사용자 범위 실행 토큰을 취소한다. */
export function cancelAutoPrepRun(runId: string, keepalive = false): Promise<void> {
  return api<void>("/auto-prep/run/cancel", {
    method: "POST",
    body: JSON.stringify({ runId }),
    keepalive,
  });
}

/** terminal event 없이 끊긴 stream만 서버 취소한다. 명시 abort는 abort listener/호출 액션이 담당한다. */
export async function cancelOrphanedAutoPrepRun(
  req: AutoPrepRequest,
  terminalReceived: boolean,
  signal?: AbortSignal,
  cancel: (runId: string, keepalive?: boolean) => Promise<void> = cancelAutoPrepRun,
): Promise<boolean> {
  if (terminalReceived || signal?.aborted || !req.runId) return false;
  await cancel(req.runId).catch(() => undefined);
  return true;
}

/** SSE 실행. plan/part-start/substep/part-done/done 이벤트를 on 콜백으로 흘려보낸다. */
export async function runStream(
  req: AutoPrepRequest,
  on: (event: PrepEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  if (signal && req.runId) {
    signal.addEventListener("abort", () => {
      void cancelAutoPrepRun(req.runId as string, true).catch(() => undefined);
    }, { once: true });
  }
  if (shouldUseMockData()) {
    await runOutageDemoStream(req, on, signal);
    return;
  }

  let terminalReceived = false;
  let streamOpened = false;
  try {
    const rawResponse = await apiRaw("/auto-prep/run/stream", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify(req),
      signal,
    });
    const res = rawResponse.response;
    try {
      if (isOutageStatus(res.status) && await activateOutageFallbackIfConfirmed()) {
        throw createOutageMutationUncertainError();
      }
      if (!res.ok) {
        const envelope = (await res.json().catch(() => null)) as ApiEnvelope<unknown> | null;
        throw new ApiError(
          envelope?.message ?? `자동 준비 실행에 실패했습니다 (${res.status})`,
          envelope?.code ?? "ERROR",
          res.status,
        );
      }
      streamOpened = true;
      if (!res.body) {
        throw new Error("자동 준비 실행 응답을 읽을 수 없습니다.");
      }
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        rawResponse.assertSessionCurrent();
        buffer += decoder.decode(value, { stream: true });
        let sep: number;
        while ((sep = buffer.indexOf("\n\n")) >= 0) {
          const rawEvent = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          const evt = parseEvent(rawEvent);
          if (evt) {
            // 계정 전환과 같은 이벤트 루프에서 도착한 마지막 조각도 UI에 반영하지 않는다.
            rawResponse.assertSessionCurrent();
            const terminal = evt.type === "done" || evt.type === "error";
            if (terminal) terminalReceived = true;
            on(evt);
            if (terminal) {
              // terminal event가 계약상 완료 신호다. 서버 cancel tombstone을 만들지 않고 reader만 닫는다.
              await reader.cancel().catch(() => undefined);
              return;
            }
          }
        }
      }
    } finally {
      rawResponse.dispose();
    }
    // clean EOF라도 terminal event가 없으면 서버 작업이 고아가 될 수 있다.
    await cancelOrphanedAutoPrepRun(req, terminalReceived, signal);
  } catch (error) {
    if (streamOpened || isNetworkOutageError(error)) {
      await cancelOrphanedAutoPrepRun(req, terminalReceived, signal);
    }
    if (isNetworkOutageError(error) && await activateOutageFallbackIfConfirmed()) {
      throw createOutageMutationUncertainError();
    }
    throw error;
  }
}

const OUTAGE_DEMO_STEPS = ["PROFILE", "JOB", "FIT", "WRITE", "INTERVIEW", "COMMUNITY"] as const;

const OUTAGE_DEMO_SUBSTEPS: Record<(typeof OUTAGE_DEMO_STEPS)[number], { name: string; desc: string }> = {
  PROFILE: { name: "프로필 미리보기", desc: "저장된 정보 대신 시연용 프로필 흐름을 확인하고 있어요." },
  JOB: { name: "공고 미리보기", desc: "시연용 채용 요건을 화면에 연결하고 있어요." },
  FIT: { name: "적합도 미리보기", desc: "시연용 입력으로 분석 화면의 연결을 확인하고 있어요." },
  WRITE: { name: "자소서 미리보기", desc: "저장되지 않는 예시 교정 흐름을 준비하고 있어요." },
  INTERVIEW: { name: "면접 미리보기", desc: "시연용 예상 질문 흐름을 준비하고 있어요." },
  COMMUNITY: { name: "후기 미리보기", desc: "시연용 커뮤니티 추천 흐름을 확인하고 있어요." },
};

function outageDemoDelay(ms: number, signal?: AbortSignal): Promise<void> {
  if (signal?.aborted) return Promise.reject(signal.reason ?? new DOMException("Aborted", "AbortError"));
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      signal?.removeEventListener("abort", abort);
      resolve();
    }, ms);
    const abort = () => {
      clearTimeout(timeout);
      reject(signal?.reason ?? new DOMException("Aborted", "AbortError"));
    };
    signal?.addEventListener("abort", abort, { once: true });
  });
}

/** AWS 장애 중에도 화면 연결을 점검할 수 있는 비영속 AutoPrep 시연 시퀀스. */
async function runOutageDemoStream(
  req: AutoPrepRequest,
  on: (event: PrepEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  on({
    type: "plan",
    plan: {
      intent: "AWS 장애 대응용 AutoPrep 데모",
      slots: {
        company: null,
        jobTitle: null,
        mode: req.mode ?? null,
        applicationCaseId: req.applicationCaseId ?? null,
      },
      steps: [...OUTAGE_DEMO_STEPS],
    },
  });

  for (const key of OUTAGE_DEMO_STEPS) {
    await outageDemoDelay(90, signal);
    on({ type: "part-start", key });
    await outageDemoDelay(90, signal);
    on({ type: "substep", key, ...OUTAGE_DEMO_SUBSTEPS[key] });
    await outageDemoDelay(120, signal);
    on({
      type: "part-done",
      result: {
        key,
        status: "DONE",
        summary: `[장애 데모] ${OUTAGE_DEMO_SUBSTEPS[key].name}가 완료됐어요. 이 결과는 저장되지 않습니다.`,
        detail: { outageDemo: true },
        elapsedMs: 300,
      },
    });
  }

  on({
    type: "done",
    message: "AWS 연결 장애로 저장되지 않는 AutoPrep 데모 결과를 보여드렸어요.",
  });
}

function parseEvent(raw: string): PrepEvent | null {
  let event = "";
  let data = "";
  for (const line of raw.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    else if (line.startsWith("data:")) data += line.slice(5).trim();
  }
  if (!event) return null;
  let payload: Record<string, unknown> = {};
  try {
    payload = data ? (JSON.parse(data) as Record<string, unknown>) : {};
  } catch {
    return null;
  }
  switch (event) {
    case "plan":
      return { type: "plan", plan: payload as never };
    case "part-start":
      return { type: "part-start", key: String(payload.key) };
    case "substep":
      return {
        type: "substep",
        key: String(payload.key),
        name: String(payload.name ?? ""),
        desc: String(payload.desc ?? ""),
      };
    case "part-done":
      return { type: "part-done", result: payload as never };
    case "done":
      return { type: "done", message: String(payload.message ?? "") };
    case "error":
      return { type: "error", message: String(payload.message ?? "오류가 발생했습니다.") };
    default:
      return null;
  }
}
