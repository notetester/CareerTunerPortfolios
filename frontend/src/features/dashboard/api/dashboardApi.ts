import { api } from "@/app/lib/api";
import type { DashboardSummary } from "../types/dashboardSummary";

export function getDashboardSummary() {
  return api<DashboardSummary>("/dashboard/summary");
}
