import { api } from "@/app/lib/api";
import type { AnalyticsPromptTemplate } from "../types/analyticsPrompt";

export function getAnalyticsPrompts() {
  return api<AnalyticsPromptTemplate[]>("/admin/prompts/analytics");
}

export function getAnalyticsPrompt(key: string) {
  return api<AnalyticsPromptTemplate>(`/admin/prompts/analytics/${key}`);
}
