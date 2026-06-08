import { api } from "@/app/lib/api";
import type { AdminApplicationCaseDetail, AdminApplicationCaseRow } from "./types";
import type { ApplicationStatus } from "@/features/applications/types/applicationCase";

export function getAdminApplicationCases(params: {
  keyword?: string;
  status?: string;
  includeArchived?: boolean;
  includeDeleted?: boolean;
  limit?: number;
} = {}): Promise<AdminApplicationCaseRow[]> {
  const search = new URLSearchParams();
  if (params.keyword) search.set("keyword", params.keyword);
  if (params.status) search.set("status", params.status);
  search.set("includeArchived", String(params.includeArchived ?? true));
  search.set("includeDeleted", String(params.includeDeleted ?? false));
  search.set("limit", String(params.limit ?? 50));
  return api<AdminApplicationCaseRow[]>(`/admin/application-cases?${search.toString()}`, { method: "GET" });
}

export function getAdminApplicationCaseDetail(id: number): Promise<AdminApplicationCaseDetail> {
  return api<AdminApplicationCaseDetail>(`/admin/application-cases/${id}`, { method: "GET" });
}

export function updateAdminApplicationCaseStatus(
  id: number,
  status: ApplicationStatus,
  memo: string,
): Promise<AdminApplicationCaseRow> {
  return api<AdminApplicationCaseRow>(`/admin/application-cases/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, memo }),
  });
}
