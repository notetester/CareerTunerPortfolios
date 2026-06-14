import { api } from "@/app/lib/api";

export interface GuidelineData {
  id: number;
  versionLabel: string;
  lede: string;
  oksJson: string;
  nosJson: string;
  rulesJson: string;
  paramsJson: string;
  publishedAt: string;
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

export function getPublishedGuideline(): Promise<GuidelineData> {
  return api<GuidelineData>("/community/guidelines/published");
}
