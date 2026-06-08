import { api } from "@/app/lib/api";
import type { ApplicationAnalysis, ApplicationCase, JobPosting } from "../types/applicationCase";

export function getApplicationCases() {
  return api<ApplicationCase[]>("/application-cases");
}

export function getApplicationCase(id: number) {
  return api<ApplicationCase>(`/application-cases/${id}`);
}

export function getJobPosting(applicationCaseId: number) {
  return api<JobPosting>(`/application-cases/${applicationCaseId}/job-posting`);
}

export function getApplicationAnalysis(applicationCaseId: number) {
  return api<ApplicationAnalysis>(`/application-cases/${applicationCaseId}/analysis`);
}

export function createMockAnalysis(applicationCaseId: number) {
  return api<ApplicationAnalysis>(`/application-cases/${applicationCaseId}/analysis/mock`, { method: "POST" });
}
