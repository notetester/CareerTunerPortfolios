import { api } from "@/app/lib/api";
import type {
  AdminAccountRow,
  AdminPermissionAuditRow,
  AdminPermissionGroupRow,
  AdminPermissionPolicyRow,
} from "./types";

export function getSuperAdmins(keyword = ""): Promise<AdminAccountRow[]> {
  const search = new URLSearchParams();
  if (keyword) search.set("keyword", keyword);
  search.set("limit", "100");
  return api<AdminAccountRow[]>(`/admin/super/admins?${search.toString()}`, { method: "GET" });
}

export function getSuperAdminDetail(userId: number): Promise<AdminAccountRow> {
  return api<AdminAccountRow>(`/admin/super/admins/${userId}`, { method: "GET" });
}

export function searchSuperUsers(keyword = ""): Promise<AdminAccountRow[]> {
  const search = new URLSearchParams();
  if (keyword) search.set("keyword", keyword);
  search.set("limit", "50");
  return api<AdminAccountRow[]>(`/admin/super/users/search?${search.toString()}`, { method: "GET" });
}

export function getSuperPermissions(): Promise<AdminPermissionPolicyRow[]> {
  return api<AdminPermissionPolicyRow[]>("/admin/super/permissions", { method: "GET" });
}

export function getSuperGroups(): Promise<AdminPermissionGroupRow[]> {
  return api<AdminPermissionGroupRow[]>("/admin/super/groups", { method: "GET" });
}

export function getSuperAudit(userId?: number): Promise<AdminPermissionAuditRow[]> {
  const search = new URLSearchParams();
  if (userId) search.set("userId", String(userId));
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
