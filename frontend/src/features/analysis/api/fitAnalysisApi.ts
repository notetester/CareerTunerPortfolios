import { api } from "@/app/lib/api";
import type { FitAnalysisDetail, FitAnalysisLearningTask } from "../types/fitAnalysis";

export function getFitAnalyses() {
  return api<FitAnalysisDetail[]>("/fit-analyses");
}

export function getFitAnalysisByApplicationCase(applicationCaseId: number) {
  return api<FitAnalysisDetail>(`/fit-analyses/application-cases/${applicationCaseId}`);
}

/**
 * 적합도 분석 생성/재생성(C 담당 AI 12~15). 백엔드는 현재 mock, API 키 주입 시 실 분석으로 전환된다.
 */
export function generateFitAnalysis(applicationCaseId: number) {
  return api<FitAnalysisDetail>(`/fit-analyses/application-cases/${applicationCaseId}`, { method: "POST" });
}

export function updateFitAnalysisLearningTask(fitAnalysisId: number, taskId: number, completed: boolean) {
  return api<FitAnalysisLearningTask>(`/fit-analyses/${fitAnalysisId}/learning-tasks/${taskId}`, {
    method: "PATCH",
    body: JSON.stringify({ completed }),
  });
}
