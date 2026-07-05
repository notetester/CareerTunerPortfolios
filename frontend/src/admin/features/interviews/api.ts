import { api } from "@/app/lib/api";
import type {
  AdminInterviewAiFailureRow,
  AdminInterviewSessionDetail,
  AdminInterviewSessionPage,
  AdminInterviewSummary,
} from "./types";

export function getAdminInterviewSummary(): Promise<AdminInterviewSummary> {
  return api<AdminInterviewSummary>("/admin/interview/summary", { method: "GET" });
}

export function getAdminInterviewSessions(params: {
  keyword?: string;
  mode?: string;
  /** true 면 리포트 생성된 세션만 (리포트 운영 화면). */
  hasReport?: boolean;
  page?: number;
  size?: number;
} = {}): Promise<AdminInterviewSessionPage> {
  const search = new URLSearchParams();
  if (params.keyword) search.set("keyword", params.keyword);
  if (params.mode) search.set("mode", params.mode);
  if (params.hasReport != null) search.set("hasReport", String(params.hasReport));
  search.set("page", String(params.page ?? 1));
  search.set("size", String(params.size ?? 20));
  return api<AdminInterviewSessionPage>(`/admin/interview/sessions?${search.toString()}`, { method: "GET" });
}

export function getAdminInterviewSessionDetail(id: number): Promise<AdminInterviewSessionDetail> {
  return api<AdminInterviewSessionDetail>(`/admin/interview/sessions/${id}`, { method: "GET" });
}

/** 면접 AI 기능(질문/꼬리질문/평가/리포트) 실패 이력. */
export function getAdminInterviewAiFailures(limit = 50): Promise<AdminInterviewAiFailureRow[]> {
  return api<AdminInterviewAiFailureRow[]>(`/admin/interview/ai-failures?limit=${limit}`, { method: "GET" });
}

/** 관리자 운영 메모 저장 (사용자 미노출). */
export function updateAdminMemo(sessionId: number, memo: string): Promise<void> {
  return api<void>(`/admin/interview/sessions/${sessionId}/memo`, {
    method: "PUT",
    body: JSON.stringify({ memo }),
  });
}
