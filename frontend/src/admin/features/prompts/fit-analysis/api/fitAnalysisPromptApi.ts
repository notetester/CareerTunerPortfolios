import { api } from "@/app/lib/api";
import type { FitAnalysisPromptTemplate } from "../types/fitAnalysisPrompt";

export function getFitAnalysisPrompts() {
  return api<FitAnalysisPromptTemplate[]>("/admin/prompts/fit-analysis");
}

export function getFitAnalysisPrompt(key: string) {
  return api<FitAnalysisPromptTemplate>(`/admin/prompts/fit-analysis/${key}`);
}
