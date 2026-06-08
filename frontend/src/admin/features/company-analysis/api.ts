import { api } from "@/app/lib/api";
import type { AdminCompanyAnalysisRow } from "./types";

export function getAdminCompanyAnalyses(limit = 50): Promise<AdminCompanyAnalysisRow[]> {
  return api<AdminCompanyAnalysisRow[]>(`/admin/company-analysis?limit=${limit}`, { method: "GET" });
}

export function updateAdminCompanyAnalysisMemo(analysisId: number, adminMemo: string): Promise<void> {
  return api<void>(`/admin/company-analysis/${analysisId}/memo`, {
    method: "PATCH",
    body: JSON.stringify({ adminMemo }),
  });
}
