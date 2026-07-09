import { api } from "@/app/lib/api";
import { apiBase } from "@/app/lib/apiBase";
import { getAccessToken } from "@/app/lib/tokenStore";
import type {
  AutoPrepIntakeResponse,
  AutoPrepRequest,
  PrepAttachedFile,
  PrepEvent,
} from "../types/autoPrep";

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
  return api<PrepAttachedFile>("/file/upload", { method: "POST", body: fd });
}

/** SSE 실행. plan/part-start/substep/part-done/done 이벤트를 on 콜백으로 흘려보낸다. */
export async function runStream(
  req: AutoPrepRequest,
  on: (event: PrepEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const token = getAccessToken();
  const res = await fetch(`${apiBase()}/auto-prep/run/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(req),
    signal,
  });
  if (!res.ok || !res.body) {
    throw new Error(`자동 준비 실행에 실패했습니다 (${res.status})`);
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let sep: number;
    while ((sep = buffer.indexOf("\n\n")) >= 0) {
      const raw = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      const evt = parseEvent(raw);
      if (evt) on(evt);
    }
  }
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
