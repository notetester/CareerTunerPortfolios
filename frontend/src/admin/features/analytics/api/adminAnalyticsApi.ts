import { api } from "@/app/lib/api";
import type {
  AdminAnalysisFailure,
  AdminAnalyticsSummary,
  AdminCareerAnalysisRun,
  AdminCareerRunMemo,
  AdminCareerRunMemoRequest,
  AdminQualityFlag,
  AdminUserTimeline,
} from "../types/adminAnalytics";

export function getAdminAnalyticsSummary() {
  return api<AdminAnalyticsSummary>("/admin/analytics/summary");
}

/** 분석 실패 큐: 적합도/장기/대시보드 분석의 FAILED·FALLBACK 결과 최신순. */
export function getAdminAnalysisFailures() {
  return api<AdminAnalysisFailure[]>("/admin/analytics/failures");
}

/** 품질 검수 큐: 최신 적합도 분석에 대한 결정적 휴리스틱 점검 항목. */
export function getAdminQualityFlags() {
  return api<AdminQualityFlag[]>("/admin/analytics/quality-flags");
}

export function resolveAdminQualityFlag(fitAnalysisId: number, flagType: string) {
  return api<void>(`/admin/analytics/quality-flags/${fitAnalysisId}/${encodeURIComponent(flagType)}/resolve`, { method: "PATCH" });
}

export function getAdminUserTimeline(userId: number) {
  return api<AdminUserTimeline[]>(`/admin/analytics/users/${userId}/timeline`);
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
