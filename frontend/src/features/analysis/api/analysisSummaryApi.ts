import { api } from "@/app/lib/api";
import { runWithAiCharge } from "@/features/billing/api/aiChargePreviewApi";
import type { AnalysisSummary } from "../types/analysisSummary";

export function getAnalysisSummary() {
  return api<AnalysisSummary>("/analysis/summary");
}

/** 사용자가 명시적으로 요청한 장기 경향 재분석. AI를 강제 실행하며 크레딧이 차감된다. */
export function refreshAnalysisSummary() {
  return runWithAiCharge("CAREER_TREND", (headers) =>
    api<AnalysisSummary>("/analysis/summary/refresh", { method: "POST", headers }));
}

/** 장기 분석 실행 이력 한 줄(사용자 노출용 — 토큰/원문은 제외하고 유형/상태/시각만 사용). */
export interface AnalysisRunHistoryItem {
  id: number;
  analysisType: string;
  status: string;
  model: string | null;
  createdAt: string;
}

/** 내 장기 분석 실행 이력(최신순). */
export function getAnalysisHistory() {
  return api<AnalysisRunHistoryItem[]>("/analysis/history");
}
