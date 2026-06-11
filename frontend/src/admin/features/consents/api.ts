import { api } from "@/app/lib/api";
import type { AdminConsentView } from "./types";

export function getAdminConsents(params: { keyword?: string; consentType?: string; limit?: number } = {}): Promise<AdminConsentView[]> {
  const search = new URLSearchParams();
  if (params.keyword) search.set("keyword", params.keyword);
  if (params.consentType) search.set("consentType", params.consentType);
  if (params.limit) search.set("limit", String(params.limit));
  const query = search.toString();
  return api<AdminConsentView[]>(`/admin/consents${query ? `?${query}` : ""}`, { method: "GET" });
}
