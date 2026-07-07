import { api } from "@/app/lib/api";
import type {
  AdminGateReviewRequest,
  AdminGateStats,
  AdminFitAnalysisDetail,
  AdminFitAnalysisMemo,
  AdminFitAnalysisMemoRequest,
  AdminFitAnalysisPage,
} from "../types/adminFitAnalysis";

/** 서버측 페이지네이션·필터 파라미터. 빈 값/기본값은 쿼리에서 생략한다. */
export interface AdminFitAnalysisListParams {
  reviewRequiredOnly?: boolean;
  query?: string;
  band?: string;
  result?: string;
  memoOnly?: boolean;
  reanalysisOnly?: boolean;
  page?: number;
  size?: number;
}

export function getAdminFitAnalyses(params: AdminFitAnalysisListParams = {}) {
  const search = new URLSearchParams();
  if (params.reviewRequiredOnly) search.set("reviewRequiredOnly", "true");
  if (params.query && params.query.trim()) search.set("query", params.query.trim());
  if (params.band && params.band !== "ALL") search.set("band", params.band);
  if (params.result && params.result !== "ALL") search.set("result", params.result);
  if (params.memoOnly) search.set("memoOnly", "true");
  if (params.reanalysisOnly) search.set("reanalysisOnly", "true");
  if (params.page && params.page > 1) search.set("page", String(params.page));
  if (params.size) search.set("size", String(params.size));
  const qs = search.toString();
  return api<AdminFitAnalysisPage>(`/admin/fit-analyses${qs ? `?${qs}` : ""}`);
}

export function getAdminGateStats() {
  return api<AdminGateStats>("/admin/fit-analyses/gate-stats");
}

export function getAdminFitAnalysis(id: number) {
  return api<AdminFitAnalysisDetail>(`/admin/fit-analyses/${id}`);
}

export function patchAdminGateReview(id: number, request: AdminGateReviewRequest) {
  return api<AdminFitAnalysisDetail>(`/admin/fit-analyses/${id}/gate-review`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}

export function getAdminFitAnalysisMemos(id: number) {
  return api<AdminFitAnalysisMemo[]>(`/admin/fit-analyses/${id}/memos`);
}

export function createAdminFitAnalysisMemo(id: number, request: AdminFitAnalysisMemoRequest) {
  return api<AdminFitAnalysisMemo>(`/admin/fit-analyses/${id}/memos`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function updateAdminFitAnalysisMemo(id: number, memoId: number, request: AdminFitAnalysisMemoRequest) {
  return api<AdminFitAnalysisMemo>(`/admin/fit-analyses/${id}/memos/${memoId}`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}

export function deleteAdminFitAnalysisMemo(id: number, memoId: number) {
  return api<void>(`/admin/fit-analyses/${id}/memos/${memoId}`, {
    method: "DELETE",
  });
}
