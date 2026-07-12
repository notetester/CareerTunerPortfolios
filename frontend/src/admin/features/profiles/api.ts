import { api } from "@/app/lib/api";
import type { AdminUserProfile, AdminUserProfileVersion } from "./types";

export function getAdminProfiles(params: { keyword?: string; limit?: number } = {}): Promise<AdminUserProfile[]> {
  const search = new URLSearchParams();
  if (params.keyword) search.set("keyword", params.keyword);
  if (params.limit) search.set("limit", String(params.limit));
  const query = search.toString();
  return api<AdminUserProfile[]>(`/admin/profiles${query ? `?${query}` : ""}`, { method: "GET" });
}

export function getAdminProfile(userId: number): Promise<AdminUserProfile> {
  return api<AdminUserProfile>(`/admin/profiles/${userId}`, { method: "GET" });
}

export function getAdminProfileVersions(userId: number, limit = 20): Promise<AdminUserProfileVersion[]> {
  return api<AdminUserProfileVersion[]>(
    `/admin/profiles/${userId}/versions?limit=${Math.max(1, Math.min(limit, 100))}`,
    { method: "GET" },
  );
}
