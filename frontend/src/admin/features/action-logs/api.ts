import { api } from "@/app/lib/api";
import type { AdminActionLogRow } from "./types";

export function getAdminActionLogs(params: {
  keyword?: string;
  actionType?: string;
  targetType?: string;
  limit?: number;
} = {}): Promise<AdminActionLogRow[]> {
  const search = new URLSearchParams();
  if (params.keyword) search.set("keyword", params.keyword);
  if (params.actionType) search.set("actionType", params.actionType);
  if (params.targetType) search.set("targetType", params.targetType);
  search.set("limit", String(params.limit ?? 100));
  return api<AdminActionLogRow[]>(`/admin/action-logs?${search.toString()}`, { method: "GET" });
}
