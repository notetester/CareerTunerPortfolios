import { api } from "@/app/lib/api";
import type { AdminInterviewSessionDetail, AdminInterviewSessionRow } from "./types";

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
