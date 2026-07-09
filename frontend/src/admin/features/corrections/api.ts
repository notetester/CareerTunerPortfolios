import { api } from "@/app/lib/api";
import type {
  AdminCorrectionDetail,
  AdminCorrectionFailureRow,
  AdminCorrectionFilters,
  AdminCorrectionPage,
  AdminCorrectionSummary,
} from "./types";

export function getAdminCorrections(filters: AdminCorrectionFilters = {}) {
  const query = new URLSearchParams();
  if (filters.keyword) query.set("keyword", filters.keyword);
  if (filters.correctionType) query.set("correctionType", filters.correctionType);
  if (filters.memoState) query.set("memoState", filters.memoState);
  query.set("page", String(filters.page ?? 1));
  query.set("size", String(filters.size ?? 20));
  return api<AdminCorrectionPage>(`/admin/corrections?${query.toString()}`, { method: "GET" });
}

export function getAdminCorrectionSummary() {
  return api<AdminCorrectionSummary>("/admin/corrections/summary", { method: "GET" });
}

export function getAdminCorrectionFailures(limit = 100) {
  return api<AdminCorrectionFailureRow[]>(`/admin/corrections/ai-failures?limit=${limit}`, { method: "GET" });
}

export function getAdminCorrection(id: number) {
  return api<AdminCorrectionDetail>(`/admin/corrections/${id}`, { method: "GET" });
}

export function updateAdminCorrectionMemo(id: number, memo: string | null) {
  return api<void>(`/admin/corrections/${id}/memo`, {
    method: "PUT",
    body: JSON.stringify({ memo }),
  });
}
