import { api } from "@/app/lib/api";
import type { AdminCompanyAnalysisRow } from "./types";

export function getAdminCompanyAnalyses(limit = 50): Promise<AdminCompanyAnalysisRow[]> {
  return api<AdminCompanyAnalysisRow[]>(`/admin/company-analysis?limit=${limit}`, { method: "GET" });
}
