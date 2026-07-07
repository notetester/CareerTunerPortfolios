import { api } from "@/app/lib/api";
import type {
  CompanyApplication,
  CompanyJobPosting,
  CompanyProfile,
  JobPostingUpsertPayload,
} from "../types/company";

// ── 기업 신청 ──

export function applyCompany(payload: {
  companyName: string;
  businessNumber?: string;
  contact: string;
  description?: string;
}): Promise<CompanyApplication> {
  return api<CompanyApplication>("/company/applications", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

/** 내 최신 신청 1건. 신청 이력이 없으면 null. */
export function getMyCompanyApplication(): Promise<CompanyApplication | null> {
  return api<CompanyApplication | null>("/company/applications/me", { method: "GET" });
}

/** 내 기업 프로필. 승인 전이면 null. */
export function getMyCompanyProfile(): Promise<CompanyProfile | null> {
  return api<CompanyProfile | null>("/company/profile/me", { method: "GET" });
}

// ── 내 공고 관리 ──

export function listMyJobPostings(): Promise<CompanyJobPosting[]> {
  return api<CompanyJobPosting[]>("/company/job-postings", { method: "GET" });
}

export function getMyJobPosting(id: number): Promise<CompanyJobPosting> {
  return api<CompanyJobPosting>(`/company/job-postings/${id}`, { method: "GET" });
}

export function createJobPosting(payload: JobPostingUpsertPayload): Promise<CompanyJobPosting> {
  return api<CompanyJobPosting>("/company/job-postings", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateJobPosting(id: number, payload: JobPostingUpsertPayload): Promise<CompanyJobPosting> {
  return api<CompanyJobPosting>(`/company/job-postings/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function closeJobPosting(id: number): Promise<CompanyJobPosting> {
  return api<CompanyJobPosting>(`/company/job-postings/${id}/close`, { method: "POST" });
}
