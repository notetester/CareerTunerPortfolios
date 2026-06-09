import { api } from "@/app/lib/api";
import type { AdminAnalyticsSummary, AdminCareerAnalysisRun } from "../types/adminAnalytics";

export function getAdminAnalyticsSummary() {
  return api<AdminAnalyticsSummary>("/admin/analytics/summary");
}

export function getAdminCareerAnalysisRuns(userId?: number) {
  const query = userId == null ? "" : `?userId=${userId}`;
  return api<AdminCareerAnalysisRun[]>(`/admin/analytics/runs${query}`);
}
