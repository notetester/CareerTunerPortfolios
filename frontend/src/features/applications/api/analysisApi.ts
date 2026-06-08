import { api } from "@/app/lib/api";
import type { CompanyAnalysis, JobAnalysis } from "../types/analysis";

export async function getJobAnalysis(applicationCaseId: number): Promise<JobAnalysis | null> {
  return (await api<JobAnalysis | null>(`/application-cases/${applicationCaseId}/job-analysis`, {
    method: "GET",
  })) ?? null;
}

export function createMockJobAnalysis(applicationCaseId: number): Promise<JobAnalysis> {
  return api<JobAnalysis>(`/application-cases/${applicationCaseId}/job-analysis/mock`, {
    method: "POST",
  });
}

export function createJobAnalysis(applicationCaseId: number): Promise<JobAnalysis> {
  return api<JobAnalysis>(`/application-cases/${applicationCaseId}/job-analysis`, {
    method: "POST",
  });
}

export async function getCompanyAnalysis(applicationCaseId: number): Promise<CompanyAnalysis | null> {
  return (await api<CompanyAnalysis | null>(`/application-cases/${applicationCaseId}/company-analysis`, {
    method: "GET",
  })) ?? null;
}

export function createMockCompanyAnalysis(applicationCaseId: number): Promise<CompanyAnalysis> {
  return api<CompanyAnalysis>(`/application-cases/${applicationCaseId}/company-analysis/mock`, {
    method: "POST",
  });
}

export function createCompanyAnalysis(applicationCaseId: number): Promise<CompanyAnalysis> {
  return api<CompanyAnalysis>(`/application-cases/${applicationCaseId}/company-analysis`, {
    method: "POST",
  });
}
