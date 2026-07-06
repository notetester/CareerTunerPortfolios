// 관리자 광고 관리 API — CRUD + 활성 토글 + 이미지 업로드(기존 file_asset 재사용).
import { api } from "@/app/lib/api";
import type { AdminAd, AdminAdPayload } from "./types/adminAd";

/** 업로드 응답(백엔드 FileAssetResponse) 중 광고에 필요한 최소 필드. */
interface UploadedFile {
  id: number;
}

export function listAds(params?: {
  placement?: string;
  platform?: string;
  activeOnly?: boolean;
}): Promise<AdminAd[]> {
  const query = new URLSearchParams();
  if (params?.placement) query.set("placement", params.placement);
  if (params?.platform) query.set("platform", params.platform);
  if (params?.activeOnly) query.set("activeOnly", "true");
  const qs = query.toString();
  return api<AdminAd[]>(`/admin/ads${qs ? `?${qs}` : ""}`, { method: "GET" });
}

export function getAd(id: number): Promise<AdminAd> {
  return api<AdminAd>(`/admin/ads/${id}`, { method: "GET" });
}

export function createAd(payload: AdminAdPayload): Promise<AdminAd> {
  return api<AdminAd>("/admin/ads", { method: "POST", body: JSON.stringify(payload) });
}

export function updateAd(id: number, payload: AdminAdPayload): Promise<AdminAd> {
  return api<AdminAd>(`/admin/ads/${id}`, { method: "PUT", body: JSON.stringify(payload) });
}

export function toggleAdActive(id: number, active: boolean): Promise<AdminAd> {
  return api<AdminAd>(`/admin/ads/${id}/toggle-active?active=${active}`, { method: "POST" });
}

export function deleteAd(id: number): Promise<void> {
  return api<void>(`/admin/ads/${id}`, { method: "DELETE" });
}

/** 광고 이미지 업로드 — file_asset 에 저장하고 file id 를 반환. */
export function uploadAdImage(file: File): Promise<number> {
  const formData = new FormData();
  formData.append("kind", "ATTACHMENT");
  formData.append("refType", "ADVERTISEMENT");
  formData.append("file", file);
  return api<UploadedFile>("/file/upload", { method: "POST", body: formData }).then((r) => r.id);
}
