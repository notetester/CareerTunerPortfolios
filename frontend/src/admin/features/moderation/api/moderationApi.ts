import { api } from "@/app/lib/api";
import type {
  ModerationPage,
  ModerationDetail,
  ModerationStats,
  ModerationReviewAction,
  ModerationReviewQueuePage,
} from "../types/moderation";

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

export function getModerationReviewQueue(page = 1, size = 5): Promise<ModerationReviewQueuePage> {
  return api<ModerationReviewQueuePage>(`/admin/ai/moderation/review-queue?page=${page}&size=${size}`);
}

export function decideModerationReview(postId: number, action: ModerationReviewAction): Promise<void> {
  return api<void>(`/admin/ai/moderation/review-queue/${postId}`, {
    method: "PATCH",
    body: JSON.stringify({ action }),
  });
}

/* ── 댓글 검열 (게시글 함수 복제, 경로만 /comments. postId 자리 = commentId) ── */

export function getCommentModerationList(params: {
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
  return api<ModerationPage>(`/admin/ai/moderation/comments?${q}`);
}

export function getCommentModerationDetail(commentId: number): Promise<ModerationDetail> {
  return api<ModerationDetail>(`/admin/ai/moderation/comments/${commentId}`);
}

export function restoreComment(commentId: number): Promise<void> {
  return api<void>(`/admin/ai/moderation/comments/${commentId}/restore`, { method: "POST" });
}

export function deleteComment(commentId: number): Promise<void> {
  return api<void>(`/admin/ai/moderation/comments/${commentId}/delete`, { method: "POST" });
}

export function getCommentModerationStats(): Promise<ModerationStats> {
  return api<ModerationStats>(`/admin/ai/moderation/comments/stats`);
}

/* ── 검열 설정 ── */

export interface ModerationSettingData {
  strictness: string;
  hideThreshold: number;
  sanctionThreshold: number;
  blockDays: number;
  // 작성 rate-limit(도배 방지) + 신고 누적 블러 임계
  reportBlurThreshold: number;
  postRateWindowSeconds: number;
  postRateMax: number;
  commentRateWindowSeconds: number;
  commentRateMax: number;
  inquiryRateWindowSeconds: number;
  inquiryRateMax: number;
  updatedAt: string;
}

export function getModerationSettings(): Promise<ModerationSettingData> {
  return api<ModerationSettingData>("/admin/ai/moderation/settings");
}

export function updateModerationSettings(data: {
  strictness?: string;
  hideThreshold?: number;
  sanctionThreshold?: number;
  blockDays?: number;
  reportBlurThreshold?: number;
  postRateWindowSeconds?: number;
  postRateMax?: number;
  commentRateWindowSeconds?: number;
  commentRateMax?: number;
  inquiryRateWindowSeconds?: number;
  inquiryRateMax?: number;
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
