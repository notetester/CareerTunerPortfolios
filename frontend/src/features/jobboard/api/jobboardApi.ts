import { api } from "@/app/lib/api";
import type { CompanyJobPosting, JobPostingPage } from "@/features/company/types/company";
import type { JobBoardAnalyzeResult, JobBoardSearchParams } from "../types/jobboard";

export function searchJobPostings(params: JobBoardSearchParams = {}): Promise<JobPostingPage> {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value) !== "") {
      query.set(key, String(value));
    }
  });
  const queryString = query.toString();
  return api<JobPostingPage>(`/job-board${queryString ? `?${queryString}` : ""}`, { method: "GET" });
}

export function getJobPosting(id: number): Promise<CompanyJobPosting> {
  return api<CompanyJobPosting>(`/job-board/${id}`, { method: "GET" });
}

/** "이 공고로 분석하기" — 공고 본문으로 지원 건을 만들고 caseId 를 받는다. */
export function analyzeJobPosting(id: number): Promise<JobBoardAnalyzeResult> {
  return api<JobBoardAnalyzeResult>(`/job-board/${id}/analyze`, { method: "POST" });
}
