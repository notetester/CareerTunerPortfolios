import { api } from "@/app/lib/api";
import type { AdminAiUsageLogRow, AdminJobAnalysisRow } from "./types";

export function getAdminJobAnalyses(limit = 50): Promise<AdminJobAnalysisRow[]> {
  return api<AdminJobAnalysisRow[]>(`/admin/job-analysis?limit=${limit}`, { method: "GET" });
}

export function getAdminBUsageLogs(limit = 50): Promise<AdminAiUsageLogRow[]> {
  return api<AdminAiUsageLogRow[]>(`/admin/ai-usage/b?limit=${limit}`, { method: "GET" });
}
