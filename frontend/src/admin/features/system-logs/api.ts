import { api } from "@/app/lib/api";

export interface AdminAiUsageLogRow {
  id: number;
  userId: number | null;
  userEmail: string | null;
  featureType: string;
  status: string;
  model: string | null;
  tokenUsage: number | null;
  creditUsed: number | null;
  errorMessage: string | null;
  createdAt: string;
}

export const getAdminAiUsageLogs = (status?: string, limit = 100) =>
  api<AdminAiUsageLogRow[]>(
    `/admin/logs/ai-usage?limit=${limit}${status ? `&status=${encodeURIComponent(status)}` : ""}`,
    { method: "GET" },
  );
