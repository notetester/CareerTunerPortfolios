import { api } from "@/app/lib/api";
import type { AnalysisSummary } from "../types/analysisSummary";

export function getAnalysisSummary() {
  return api<AnalysisSummary>("/analysis/summary");
}
