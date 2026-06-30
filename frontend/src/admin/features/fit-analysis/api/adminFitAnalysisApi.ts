import { api } from "@/app/lib/api";
import type {
  AdminFitAnalysisDetail,
  AdminFitAnalysisListItem,
  AdminFitAnalysisMemo,
  AdminFitAnalysisMemoRequest,
} from "../types/adminFitAnalysis";

export function getAdminFitAnalyses(reviewRequiredOnly = false) {
  const query = reviewRequiredOnly ? "?reviewRequiredOnly=true" : "";
  return api<AdminFitAnalysisListItem[]>(`/admin/fit-analyses${query}`);
}

export function getAdminFitAnalysis(id: number) {
  return api<AdminFitAnalysisDetail>(`/admin/fit-analyses/${id}`);
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
