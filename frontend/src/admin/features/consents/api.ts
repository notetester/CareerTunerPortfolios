import { api } from "@/app/lib/api";
import type { AdminConsentView } from "./types";

export function getAdminConsents(params: {
  keyword?: string;
  consentType?: string;
  status?: string;
  source?: string;
  from?: string;
  to?: string;
  limit?: number;
} = {}): Promise<AdminConsentView[]> {
  const search = new URLSearchParams();
  if (params.keyword) search.set("keyword", params.keyword);
  if (params.consentType) search.set("consentType", params.consentType);
  if (params.status) search.set("status", params.status);
  if (params.source) search.set("source", params.source);
  if (params.from) search.set("from", params.from);
  if (params.to) search.set("to", params.to);
  if (params.limit) search.set("limit", String(params.limit));
  const query = search.toString();
  return api<AdminConsentView[]>(`/admin/consents${query ? `?${query}` : ""}`, { method: "GET" });
}
