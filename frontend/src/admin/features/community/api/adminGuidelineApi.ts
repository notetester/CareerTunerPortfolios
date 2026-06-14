import { api } from "@/app/lib/api";

export interface AdminGuidelineResponse {
  id: number;
  versionLabel: string;
  summary: string;
  lede: string;
  oksJson: string;
  nosJson: string;
  rulesJson: string;
  paramsJson: string;
  status: string;
  enforceType: string;
  scheduledAt: string | null;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface GuidelineRule {
  t: string;
  s: number;
  b: string;
}

export interface GuidelineParams {
  blind: number;
  sla: number;
  expire: number;
  s1: number;
  s2: number;
  appeal: number;
}

export interface AdminGuidelineRequest {
  versionLabel?: string;
  summary?: string;
  lede?: string;
  oks?: string[];
  nos?: string[];
  rules?: GuidelineRule[];
  params?: GuidelineParams;
  enforceType?: string;
  scheduledAt?: string | null;
}

export function getGuidelines(): Promise<AdminGuidelineResponse[]> {
  return api<AdminGuidelineResponse[]>("/admin/guidelines");
}

export function getGuideline(id: number): Promise<AdminGuidelineResponse> {
  return api<AdminGuidelineResponse>(`/admin/guidelines/${id}`);
}

export function createGuideline(data: AdminGuidelineRequest): Promise<AdminGuidelineResponse> {
  return api<AdminGuidelineResponse>("/admin/guidelines", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function updateGuideline(id: number, data: AdminGuidelineRequest): Promise<AdminGuidelineResponse> {
  return api<AdminGuidelineResponse>(`/admin/guidelines/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export function publishGuideline(id: number): Promise<AdminGuidelineResponse> {
  return api<AdminGuidelineResponse>(`/admin/guidelines/${id}/publish`, {
    method: "POST",
  });
}

export function deleteGuideline(id: number): Promise<void> {
  return api<void>(`/admin/guidelines/${id}`, { method: "DELETE" });
}
