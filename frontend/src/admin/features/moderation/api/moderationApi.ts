import { api } from "@/app/lib/api";
import type { ModerationPage, ModerationDetail, ModerationStats } from "../types/moderation";

export function getModerationList(params: {
  status?: string;
  toxic?: boolean;
  page?: number;
  size?: number;
}): Promise<ModerationPage> {
  const q = new URLSearchParams();
  if (params.status) q.set("status", params.status);
  if (params.toxic !== undefined) q.set("toxic", String(params.toxic));
  q.set("page", String(params.page ?? 1));
  q.set("size", String(params.size ?? 20));
  return api<ModerationPage>(`/admin/ai/moderation?${q}`);
}

export function getModerationDetail(postId: number): Promise<ModerationDetail> {
  return api<ModerationDetail>(`/admin/ai/moderation/${postId}`);
}

export function restorePost(postId: number): Promise<void> {
  return api<void>(`/admin/ai/moderation/${postId}/restore`, { method: "POST" });
}

export function deletePost(postId: number): Promise<void> {
  return api<void>(`/admin/ai/moderation/${postId}/delete`, { method: "POST" });
}

export function getModerationStats(): Promise<ModerationStats> {
  return api<ModerationStats>(`/admin/ai/moderation/stats`);
}

/* ── 검열 설정 ── */

export interface ModerationSettingData {
  strictness: string;
  hideThreshold: number;
  updatedAt: string;
}

export function getModerationSettings(): Promise<ModerationSettingData> {
  return api<ModerationSettingData>("/admin/ai/moderation/settings");
}

export function updateModerationSettings(data: {
  strictness?: string;
  hideThreshold?: number;
}): Promise<ModerationSettingData> {
  return api<ModerationSettingData>("/admin/ai/moderation/settings", {
    method: "PATCH",
    body: JSON.stringify(data),
  });
}

/* ── 검열 테스트 ── */

export interface ModerationTestResult {
  toxic: boolean;
  category: string;
  confidence: number;
  elapsedMs: number;
}

export function testModeration(data: {
  title?: string;
  content: string;
}): Promise<ModerationTestResult> {
  return api<ModerationTestResult>("/admin/ai/moderation-test", {
    method: "POST",
    body: JSON.stringify(data),
  });
}
