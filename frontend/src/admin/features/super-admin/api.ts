import { api } from "@/app/lib/api";
import type {
  AdminAccountRow,
  AdminPermissionAuditRow,
  AdminPermissionGroupRow,
  AdminPermissionPolicyRow,
  AdminPermissionRequest,
} from "./types";

export interface SuperSortParams {
  sortBy?: string;
  sortDir?: "ASC" | "DESC";
}

function appendSortParams(search: URLSearchParams, sort?: SuperSortParams) {
  if (sort?.sortBy) search.set("sortBy", sort.sortBy);
  if (sort?.sortDir) search.set("sortDir", sort.sortDir);
}

export function getSuperAdmins(keyword = "", sort?: SuperSortParams): Promise<AdminAccountRow[]> {
  const search = new URLSearchParams();
  if (keyword) search.set("keyword", keyword);
  appendSortParams(search, sort);
  search.set("limit", "100");
  return api<AdminAccountRow[]>(`/admin/super/admins?${search.toString()}`, { method: "GET" });
}

export function getSuperAdminDetail(userId: number): Promise<AdminAccountRow> {
  return api<AdminAccountRow>(`/admin/super/admins/${userId}`, { method: "GET" });
}

export function searchSuperUsers(keyword = "", sort?: SuperSortParams): Promise<AdminAccountRow[]> {
  const search = new URLSearchParams();
  if (keyword) search.set("keyword", keyword);
  appendSortParams(search, sort);
  search.set("limit", "50");
  return api<AdminAccountRow[]>(`/admin/super/users/search?${search.toString()}`, { method: "GET" });
}

export function getSuperPermissions(): Promise<AdminPermissionPolicyRow[]> {
  return api<AdminPermissionPolicyRow[]>("/admin/super/permissions", { method: "GET" });
}

export function getSuperGroups(): Promise<AdminPermissionGroupRow[]> {
  return api<AdminPermissionGroupRow[]>("/admin/super/groups", { method: "GET" });
}

export function getSuperAudit(userId?: number, sort?: SuperSortParams): Promise<AdminPermissionAuditRow[]> {
  const search = new URLSearchParams();
  if (userId) search.set("userId", String(userId));
  appendSortParams(search, sort);
  search.set("limit", "100");
  return api<AdminPermissionAuditRow[]>(`/admin/super/audit?${search.toString()}`, { method: "GET" });
}

export function updateSuperAdminRole(userId: number, role: string, reason: string): Promise<AdminAccountRow> {
  return api<AdminAccountRow>(`/admin/super/admins/${userId}/role`, {
    method: "PATCH",
    body: JSON.stringify({ role, reason }),
  });
}

export function grantSuperPermission(userId: number, code: string, reason: string): Promise<AdminAccountRow> {
  return api<AdminAccountRow>(`/admin/super/admins/${userId}/permissions`, {
    method: "POST",
    body: JSON.stringify({ code, reason }),
  });
}

export function revokeSuperPermission(userId: number, code: string, reason: string): Promise<AdminAccountRow> {
  return api<AdminAccountRow>(`/admin/super/admins/${userId}/permissions/revoke`, {
    method: "PATCH",
    body: JSON.stringify({ code, reason }),
  });
}

export function assignSuperGroup(userId: number, code: string, reason: string): Promise<AdminAccountRow> {
  return api<AdminAccountRow>(`/admin/super/admins/${userId}/groups`, {
    method: "POST",
    body: JSON.stringify({ code, reason }),
  });
}

export function revokeSuperGroup(userId: number, code: string, reason: string): Promise<AdminAccountRow> {
  return api<AdminAccountRow>(`/admin/super/admins/${userId}/groups/revoke`, {
    method: "PATCH",
    body: JSON.stringify({ code, reason }),
  });
}

export function getPermissionRequests(status = "PENDING"): Promise<AdminPermissionRequest[]> {
  return api<AdminPermissionRequest[]>(`/admin/super/permission-requests?status=${status}&limit=200`);
}

export function requestPermissions(userId: number, permissionCodes: string[], description?: string): Promise<void> {
  return api<void>("/admin/super/permission-requests", {
    method: "POST",
    body: JSON.stringify({ userId, permissionCodes, description }),
  });
}

export function approvePermissionRequest(id: number): Promise<void> {
  return api<void>(`/admin/super/permission-requests/${id}/approve`, { method: "POST" });
}

export function rejectPermissionRequest(id: number, reason?: string): Promise<void> {
  return api<void>(`/admin/super/permission-requests/${id}/reject`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

export function bulkGrantPermissions(userIds: number[], permissionCodes: string[], reason?: string): Promise<number> {
  return api<number>("/admin/super/bulk/grant-permissions", {
    method: "POST",
    body: JSON.stringify({ userIds, permissionCodes, reason }),
  });
}

export function bulkRevokeAdmins(userIds: number[], reason?: string): Promise<number> {
  return api<number>("/admin/super/bulk/revoke-admins", {
    method: "POST",
    body: JSON.stringify({ userIds, reason }),
  });
}
