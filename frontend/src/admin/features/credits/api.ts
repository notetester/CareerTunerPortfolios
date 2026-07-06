import { api } from "@/app/lib/api";
import type {
  AdminCreditAdjustRequest,
  AdminCreditAdjustResponse,
  AdminCreditFilters,
  AdminCreditPage,
  AdminCreditSummary,
} from "./types";

export function getAdminCredits(filters: AdminCreditFilters = {}) {
  const query = new URLSearchParams();
  if (filters.keyword) query.set("keyword", filters.keyword);
  if (filters.userId !== undefined) query.set("userId", String(filters.userId));
  if (filters.type) query.set("type", filters.type);
  query.set("page", String(filters.page ?? 1));
  query.set("size", String(filters.size ?? 20));
  return api<AdminCreditPage>(`/admin/credits?${query.toString()}`, { method: "GET" });
}

export function getAdminCreditSummary() {
  return api<AdminCreditSummary>("/admin/credits/summary", { method: "GET" });
}

export function adjustAdminCredit(request: AdminCreditAdjustRequest) {
  return api<AdminCreditAdjustResponse>("/admin/credits/adjust", {
    method: "POST",
    body: JSON.stringify(request),
  });
}
