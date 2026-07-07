// 기업 운영 콘솔 API — 기업 신청 승인/반려 + 채용공고 검토 큐.
// 응답 도메인 타입은 사용자측 features/company/types/company 를 공유한다.
import { api } from "@/app/lib/api";
import type {
  CompanyApplication,
  CompanyJobPosting,
  JobPostingFields,
  TrustGrade,
} from "@/features/company/types/company";

/** 검토 큐 행 — 백엔드 JobPostingReviewRow 와 1:1. */
export interface JobPostingReviewRow {
  reviewType: "CREATE" | "UPDATE";
  postingId: number;
  revisionId: number | null;
  title: string;
  jobRole: string;
  companyName: string | null;
  trustGrade: TrustGrade | null;
  submittedAt: string | null;
}

/** 검토 상세 — 현재 게시본 + 대기 중 변경본(수정 검토면 diff 비교 대상). */
export interface JobPostingReviewDetail {
  posting: CompanyJobPosting;
  pendingRevisionId: number | null;
  pendingRevision: (JobPostingFields & { submit?: boolean }) | null;
}

// ── 기업 신청 ──

export function listCompanyApplications(status?: string): Promise<CompanyApplication[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return api<CompanyApplication[]>(`/admin/company/applications${query}`, { method: "GET" });
}

export function approveCompanyApplication(id: number): Promise<CompanyApplication> {
  return api<CompanyApplication>(`/admin/company/applications/${id}/approve`, { method: "POST" });
}

export function rejectCompanyApplication(id: number, reason: string): Promise<CompanyApplication> {
  return api<CompanyApplication>(`/admin/company/applications/${id}/reject`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

// ── 공고 검토 ──

export function fetchJobPostingReviewQueue(): Promise<JobPostingReviewRow[]> {
  return api<JobPostingReviewRow[]>("/admin/company/job-postings", { method: "GET" });
}

export function fetchJobPostingReviewDetail(postingId: number): Promise<JobPostingReviewDetail> {
  return api<JobPostingReviewDetail>(`/admin/company/job-postings/${postingId}`, { method: "GET" });
}

export function approveJobPostingReview(postingId: number): Promise<void> {
  return api<void>(`/admin/company/job-postings/${postingId}/approve`, { method: "POST" });
}

export function rejectJobPostingReview(postingId: number, reason: string): Promise<void> {
  return api<void>(`/admin/company/job-postings/${postingId}/reject`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}
