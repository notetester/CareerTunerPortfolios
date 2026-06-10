import { api } from "@/app/lib/api";
import type {
  AdminAnalyticsSummary,
  AdminCareerAnalysisRun,
  AdminCareerRunMemo,
  AdminCareerRunMemoRequest,
} from "../types/adminAnalytics";

export function getAdminAnalyticsSummary() {
  return api<AdminAnalyticsSummary>("/admin/analytics/summary");
}

export function getAdminCareerAnalysisRuns(userId?: number) {
  const query = userId == null ? "" : `?userId=${userId}`;
  return api<AdminCareerAnalysisRun[]>(`/admin/analytics/runs${query}`);
}

// 실행 이력 운영 메모 (분석 결과 운영 메모)
export function getAdminCareerRunMemos(runId: number) {
  return api<AdminCareerRunMemo[]>(`/admin/analytics/runs/${runId}/memos`);
}

export function createAdminCareerRunMemo(runId: number, request: AdminCareerRunMemoRequest) {
  return api<AdminCareerRunMemo>(`/admin/analytics/runs/${runId}/memos`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function updateAdminCareerRunMemo(runId: number, memoId: number, request: AdminCareerRunMemoRequest) {
  return api<AdminCareerRunMemo>(`/admin/analytics/runs/${runId}/memos/${memoId}`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}

export function deleteAdminCareerRunMemo(runId: number, memoId: number) {
  return api<void>(`/admin/analytics/runs/${runId}/memos/${memoId}`, {
    method: "DELETE",
  });
}
