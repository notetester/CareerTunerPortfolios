import { api, apiRaw, createOutageMutationUncertainError } from "@/app/lib/api";
import {
  activateOutageFallbackIfConfirmed,
  isNetworkOutageError,
  isOutageFallbackActive,
  isOutageStatus,
} from "@/app/lib/outageFallback";
import type {
  AutoPrepIntakeResponse,
  AutoPrepRequest,
  PrepAttachedFile,
  PrepEvent,
} from "../types/autoPrep";
import {
  discardPendingAutoPrepFiles,
  trackPendingAutoPrepUpload,
} from "@/app/lib/pendingAutoPrepFiles";

// SSE 는 ApiResponse envelope 를 안 타므로 api() 래퍼를 못 쓴다 → fetch 직접 + 토큰 수동 첨부.
// 베이스 URL 은 apiBase() 단일 소스를 사용한다(런타임 오버라이드 반영).

/** 인테이크: 한 줄 요청 해석 + 슬롯 확인(미리보기). ready=true 면 그대로 run. */
export function intake(req: AutoPrepRequest) {
  return api<AutoPrepIntakeResponse>("/auto-prep/intake", {
    method: "POST",
    body: JSON.stringify(req),
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

/** SSE 실행. plan/part-start/substep/part-done/done 이벤트를 on 콜백으로 흘려보낸다. */
export async function runStream(
  req: AutoPrepRequest,
  on: (event: PrepEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  if (isOutageFallbackActive()) {
    await runOutageDemoStream(req, on, signal);
    if (req.attachmentFileIds?.length) {
      await discardPendingAutoPrepFiles(req.attachmentFileIds);
    }
    return;
  }

  try {
    let completed = false;
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
    if (!res.ok || !res.body) {
      throw new Error(`자동 준비 실행에 실패했습니다 (${res.status})`);
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
          on(evt);
          if (evt.type === "done") completed = true;
        }
      }
    }
    if (completed) {
      // 첨부는 실행 입력으로만 쓰이며 산출물의 영속 참조가 아니다. 정상 완료 뒤 즉시 회수한다.
      if (req.attachmentFileIds?.length) {
        await discardPendingAutoPrepFiles(req.attachmentFileIds);
      }
    }
    } finally {
      rawResponse.dispose();
    }
  } catch (error) {
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
