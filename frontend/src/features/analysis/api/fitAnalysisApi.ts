import { api } from "@/app/lib/api";
import { runWithAiCharge } from "@/features/billing/api/aiChargePreviewApi";
import type { FitAnalysisDetail, FitAnalysisHistoryEntry, FitAnalysisLearningTask } from "../types/fitAnalysis";

export function getFitAnalyses() {
  return api<FitAnalysisDetail[]>("/fit-analyses");
}

export function getFitAnalysisByApplicationCase(applicationCaseId: number) {
  return api<FitAnalysisDetail>(`/fit-analyses/application-cases/${applicationCaseId}`);
}

/** 재분석 히스토리(최신순). 직전 분석 대비 점수·역량 변화를 포함한다. */
export function getFitAnalysisHistory(applicationCaseId: number) {
  return api<FitAnalysisHistoryEntry[]>(`/fit-analyses/application-cases/${applicationCaseId}/history`);
}

/**
 * 적합도 분석 생성/재생성(C 담당 AI 12~15). 백엔드는 현재 mock, API 키 주입 시 실 분석으로 전환된다.
 */
export function generateFitAnalysis(applicationCaseId: number, certificateStrategy = false) {
  // certificateStrategy=true(학습/자격증 탭 요청)면 자격증 관점을 함께 평가한다(무조건 추천은 아님).
  const query = certificateStrategy ? "?certificateStrategy=true" : "";
  return runWithAiCharge("FIT_ANALYSIS", (headers) =>
    api<FitAnalysisDetail>(`/fit-analyses/application-cases/${applicationCaseId}${query}`, { method: "POST", headers }));
}

export function updateFitAnalysisLearningTask(fitAnalysisId: number, taskId: number, completed: boolean) {
  return api<FitAnalysisLearningTask>(`/fit-analyses/${fitAnalysisId}/learning-tasks/${taskId}`, {
    method: "PATCH",
    body: JSON.stringify({ completed }),
  });
}
