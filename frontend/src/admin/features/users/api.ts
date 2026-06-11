import { api } from "@/app/lib/api";
import type { AdminUserDetail, AdminUserRow, AdminUserStatus } from "./types";

export function getAdminUsers(params: {
  keyword?: string;
  status?: string;
  role?: string;
  limit?: number;
} = {}): Promise<AdminUserRow[]> {
  const search = new URLSearchParams();
  if (params.keyword) search.set("keyword", params.keyword);
  if (params.status) search.set("status", params.status);
  if (params.role) search.set("role", params.role);
  search.set("limit", String(params.limit ?? 50));
  return api<AdminUserRow[]>(`/admin/users?${search.toString()}`, { method: "GET" });
}

export function getAdminUserDetail(id: number): Promise<AdminUserDetail> {
  return api<AdminUserDetail>(`/admin/users/${id}`, { method: "GET" });
}

export function updateAdminUserStatus(
  id: number,
  payload: { status: AdminUserStatus; reason?: string; memo?: string; blockedUntil?: string | null },
): Promise<AdminUserRow> {
  return api<AdminUserRow>(`/admin/users/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}
