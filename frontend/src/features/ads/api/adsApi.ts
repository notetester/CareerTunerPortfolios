import { api } from "@/app/lib/api";

export interface AdCampaign {
  id: number;
  title: string;
  body?: string | null;
  surface: "WEB" | "MOBILE" | "DESKTOP" | "ALL" | string;
  placement: string;
  creativeType: "BANNER" | "CARD" | "TEXT" | string;
  imageUrl?: string | null;
  targetUrl?: string | null;
  visibleToPlans: string[];
  startsAt?: string | null;
  endsAt?: string | null;
  priority: number;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface AdCampaignRequest {
  title: string;
  body?: string | null;
  surface: string;
  placement?: string | null;
  creativeType?: string | null;
  imageUrl?: string | null;
  targetUrl?: string | null;
  visibleToPlans?: string[];
  startsAt?: string | null;
  endsAt?: string | null;
  priority?: number;
  active?: boolean;
}

export function getAds(surface: "WEB" | "MOBILE" | "DESKTOP" = "WEB") {
  return api<AdCampaign[]>(`/ads?surface=${surface}`, { method: "GET" });
}

export function recordAdEvent(id: number, surface: string, eventType: "IMPRESSION" | "CLICK") {
  return api<void>(`/ads/${id}/events`, {
    method: "POST",
    body: JSON.stringify({ surface, eventType }),
  }, { auth: false });
}

export function listAdminAds(params: { surface?: string; active?: boolean; keyword?: string } = {}) {
  const search = new URLSearchParams();
  if (params.surface) search.set("surface", params.surface);
  if (params.active !== undefined) search.set("active", String(params.active));
  if (params.keyword) search.set("keyword", params.keyword);
  const query = search.toString();
  return api<AdCampaign[]>(`/admin/ads${query ? `?${query}` : ""}`, { method: "GET" });
}

export function createAdminAd(payload: AdCampaignRequest) {
  return api<AdCampaign>("/admin/ads", { method: "POST", body: JSON.stringify(payload) });
}

export function updateAdminAd(id: number, payload: AdCampaignRequest) {
  return api<AdCampaign>(`/admin/ads/${id}`, { method: "PATCH", body: JSON.stringify(payload) });
}
