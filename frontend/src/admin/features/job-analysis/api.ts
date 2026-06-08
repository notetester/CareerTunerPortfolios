import { api } from "@/app/lib/api";
import type { AdminAiUsageLogRow, AdminJobAnalysisRow } from "./types";

export function getAdminJobAnalyses(limit = 50): Promise<AdminJobAnalysisRow[]> {
  return api<AdminJobAnalysisRow[]>(`/admin/job-analysis?limit=${limit}`, { method: "GET" });
}

export function getAdminBUsageLogs(limit = 50): Promise<AdminAiUsageLogRow[]> {
  return api<AdminAiUsageLogRow[]>(`/admin/ai-usage/b?limit=${limit}`, { method: "GET" });
}

export function getFilteredAdminBUsageLogs(params: {
  featureType?: string;
  status?: string;
  limit?: number;
}): Promise<AdminAiUsageLogRow[]> {
  const search = new URLSearchParams();
  search.set("limit", String(params.limit ?? 50));
  if (params.featureType) search.set("featureType", params.featureType);
  if (params.status) search.set("status", params.status);
  return api<AdminAiUsageLogRow[]>(`/admin/ai-usage/b?${search.toString()}`, { method: "GET" });
}

export function updateAdminJobAnalysisMemo(analysisId: number, adminMemo: string): Promise<void> {
  return api<void>(`/admin/job-analysis/${analysisId}/memo`, {
    method: "PATCH",
    body: JSON.stringify({ adminMemo }),
  });
}
