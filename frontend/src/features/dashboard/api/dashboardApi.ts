import { api } from "@/app/lib/api";
import type { DashboardSummary } from "../types/dashboardSummary";

export function getDashboardSummary() {
  return api<DashboardSummary>("/dashboard/summary");
}

/** 사용자가 명시적으로 요청한 대시보드 요약 재생성. AI를 강제 실행하며 크레딧이 차감된다. */
export function refreshDashboardSummary() {
  return api<DashboardSummary>("/dashboard/summary/refresh", { method: "POST" });
}
