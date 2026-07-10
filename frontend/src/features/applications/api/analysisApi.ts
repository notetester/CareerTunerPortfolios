import { api } from "@/app/lib/api";
import { runWithAiCharge } from "@/features/billing/api/aiChargePreviewApi";
import type {
  CompanyAnalysis,
  CompanyAnalysisReviewRequest,
  BAnalysisFailureLog,
  JobAnalysis,
  JobAnalysisReviewRequest,
} from "../types/analysis";

export function getBAnalysisFailureLogs(applicationCaseId: number, limit = 5): Promise<BAnalysisFailureLog[]> {
  return api<BAnalysisFailureLog[]>(
    `/application-cases/${applicationCaseId}/ai-usage/b/failures?limit=${limit}`,
    { method: "GET" },
  );
}

/** 지원 건 분석 종합(공고 분석 + 적합도 분석)을 한 번에 조회한다 — GET /application-cases/{id}/analysis. */
export interface ApplicationCaseAnalysisOverview {
  jobAnalysis: unknown | null;
  fitAnalysis: unknown | null;
}

export function getApplicationCaseAnalysisOverview(
  applicationCaseId: number,
): Promise<ApplicationCaseAnalysisOverview> {
  return api<ApplicationCaseAnalysisOverview>(`/application-cases/${applicationCaseId}/analysis`, { method: "GET" });
}

export async function getJobAnalysis(applicationCaseId: number): Promise<JobAnalysis | null> {
  return (await api<JobAnalysis | null>(`/application-cases/${applicationCaseId}/job-analysis`, {
    method: "GET",
  })) ?? null;
}

/**
 * strict 수동 재분석 — provider 필수(백엔드가 누락·무효를 400 으로 거절). 사용자가 picker 로 고른
 * 단일 provider 만 시도하며 교차 폴백·self-rules 안전망이 없다(자동 체인은 초기 파이프라인 전용).
 */
export function createJobAnalysis(applicationCaseId: number, provider: string): Promise<JobAnalysis> {
  const params = new URLSearchParams({ provider });
  return runWithAiCharge("JOB_ANALYSIS", (headers) =>
    api<JobAnalysis>(`/application-cases/${applicationCaseId}/job-analysis?${params}`, {
      method: "POST",
      headers,
    }));
}

export function getJobAnalysisHistory(applicationCaseId: number): Promise<JobAnalysis[]> {
  return api<JobAnalysis[]>(`/application-cases/${applicationCaseId}/job-analysis/history`, {
    method: "GET",
  });
}

export function reviewJobAnalysis(
  applicationCaseId: number,
  analysisId: number,
  request: JobAnalysisReviewRequest,
): Promise<JobAnalysis> {
  return api<JobAnalysis>(`/application-cases/${applicationCaseId}/job-analysis/${analysisId}/review`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}

export async function getCompanyAnalysis(applicationCaseId: number): Promise<CompanyAnalysis | null> {
  return (await api<CompanyAnalysis | null>(`/application-cases/${applicationCaseId}/company-analysis`, {
    method: "GET",
  })) ?? null;
}

/** strict 수동 재분석 — provider 필수(백엔드가 누락·무효를 400 으로 거절). {@link createJobAnalysis} 와 동일 정책. */
export function createCompanyAnalysis(applicationCaseId: number, provider: string): Promise<CompanyAnalysis> {
  const params = new URLSearchParams({ provider });
  return runWithAiCharge("COMPANY_RESEARCH", (headers) =>
    api<CompanyAnalysis>(`/application-cases/${applicationCaseId}/company-analysis?${params}`, {
      method: "POST",
      headers,
    }));
}

export function getCompanyAnalysisHistory(applicationCaseId: number): Promise<CompanyAnalysis[]> {
  return api<CompanyAnalysis[]>(`/application-cases/${applicationCaseId}/company-analysis/history`, {
    method: "GET",
  });
}

export function reviewCompanyAnalysis(
  applicationCaseId: number,
  analysisId: number,
  request: CompanyAnalysisReviewRequest,
): Promise<CompanyAnalysis> {
  return api<CompanyAnalysis>(`/application-cases/${applicationCaseId}/company-analysis/${analysisId}/review`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}
