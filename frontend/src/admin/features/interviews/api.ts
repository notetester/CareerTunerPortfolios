import { api } from "@/app/lib/api";
import type { AdminInterviewAiFailureRow, AdminInterviewSessionDetail, AdminInterviewSessionRow } from "./types";

export function getAdminInterviewSessions(params: {
  keyword?: string;
  mode?: string;
  limit?: number;
} = {}): Promise<AdminInterviewSessionRow[]> {
  const search = new URLSearchParams();
  if (params.keyword) search.set("keyword", params.keyword);
  if (params.mode) search.set("mode", params.mode);
  search.set("limit", String(params.limit ?? 50));
  return api<AdminInterviewSessionRow[]>(`/admin/interview/sessions?${search.toString()}`, { method: "GET" });
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
