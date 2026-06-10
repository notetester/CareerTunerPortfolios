import { api } from "@/app/lib/api";
import type { AnalysisSummary } from "../types/analysisSummary";

export function getAnalysisSummary() {
  return api<AnalysisSummary>("/analysis/summary");
}

/** 사용자가 명시적으로 요청한 장기 경향 재분석. AI를 강제 실행하며 크레딧이 차감된다. */
export function refreshAnalysisSummary() {
  return api<AnalysisSummary>("/analysis/summary/refresh", { method: "POST" });
}
