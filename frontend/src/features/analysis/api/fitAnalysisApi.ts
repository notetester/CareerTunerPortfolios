import { api } from "@/app/lib/api";
import type { FitAnalysisDetail } from "../types/fitAnalysis";

export function getFitAnalyses() {
  return api<FitAnalysisDetail[]>("/fit-analyses");
}

export function getFitAnalysisByApplicationCase(applicationCaseId: number) {
  return api<FitAnalysisDetail>(`/fit-analyses/application-cases/${applicationCaseId}`);
}
