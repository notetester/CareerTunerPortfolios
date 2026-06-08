import { api } from "@/app/lib/api";
import type { AdminAnalyticsSummary } from "../types/adminAnalytics";

export function getAdminAnalyticsSummary() {
  return api<AdminAnalyticsSummary>("/admin/analytics/summary");
}
