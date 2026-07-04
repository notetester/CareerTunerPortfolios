import { api } from "@/app/lib/api";

export interface EnterpriseApplication {
  id: number;
  userId: number;
  userEmail?: string | null;
  userName?: string | null;
  companyName: string;
  businessNumber?: string | null;
  representativeName?: string | null;
  contactName?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  websiteUrl?: string | null;
  industry?: string | null;
  employeeCount?: string | null;
  evidenceFileUrl?: string | null;
  status: string;
  reviewMemo?: string | null;
  createdAt: string;
  updatedAt?: string;
}

export interface EnterprisePolicy {
  trusted: boolean;
  createRequiresReview: boolean;
  editRequiresReview: boolean;
  maxActivePosts: number;
}

export interface EnterpriseStatus {
  employer: boolean;
  latestApplication: EnterpriseApplication | null;
  policy: EnterprisePolicy;
}

export interface EnterpriseJob {
  id: number;
  companyUserId: number;
  ownerEmail?: string | null;
  ownerName?: string | null;
  companyName: string;
  title: string;
  positionTitle: string;
  jobCategory?: string | null;
  specialties: string[];
  duties: string;
  qualifications?: string | null;
  preferred?: string | null;
  benefits?: string | null;
  employmentType?: string | null;
  experienceLevel?: string | null;
  educationLevel?: string | null;
  salaryType?: string | null;
  salaryMin?: number | null;
  salaryMax?: number | null;
  salaryText?: string | null;
  workLocation?: string | null;
  workSchedule?: string | null;
  headcount?: string | null;
  applicationStartAt?: string | null;
  applicationEndAt?: string | null;
  applyUrl?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  visibility: string;
  status: string;
  reviewStatus: string;
  reviewMemo?: string | null;
  pendingRevision: boolean;
  communityPostId?: number | null;
  createdAt: string;
  updatedAt?: string;
}

export type EnterpriseJobRequest = Omit<
  EnterpriseJob,
  "id" | "companyUserId" | "ownerEmail" | "ownerName" | "status" | "reviewStatus" | "reviewMemo" |
  "pendingRevision" | "communityPostId" | "createdAt" | "updatedAt"
>;

export function getEnterpriseStatus() {
  return api<EnterpriseStatus>("/enterprise/me", { method: "GET" });
}

export function applyEnterprise(payload: Partial<EnterpriseApplication> & {
  createRequiresReview?: boolean;
  editRequiresReview?: boolean;
}) {
  return api<EnterpriseApplication>("/enterprise/applications", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function listEnterpriseJobs() {
  return api<EnterpriseJob[]>("/enterprise/jobs", { method: "GET" });
}

export function createEnterpriseJob(payload: EnterpriseJobRequest) {
  return api<EnterpriseJob>("/enterprise/jobs", { method: "POST", body: JSON.stringify(payload) });
}

export function updateEnterpriseJob(id: number, payload: EnterpriseJobRequest) {
  return api<EnterpriseJob>(`/enterprise/jobs/${id}`, { method: "PUT", body: JSON.stringify(payload) });
}

export function listPublicEnterpriseJobs(keyword = "") {
  const q = keyword.trim() ? `?keyword=${encodeURIComponent(keyword.trim())}` : "";
  return api<EnterpriseJob[]>(`/enterprise/jobs/public${q}`, { method: "GET" }, { auth: false });
}

export function listAdminEnterpriseApplications(params: { status?: string; keyword?: string } = {}) {
  const search = new URLSearchParams();
  if (params.status) search.set("status", params.status);
  if (params.keyword) search.set("keyword", params.keyword);
  const q = search.toString();
  return api<EnterpriseApplication[]>(`/admin/enterprise/applications${q ? `?${q}` : ""}`, { method: "GET" });
}

export function reviewAdminEnterpriseApplication(id: number, payload: {
  status: "APPROVED" | "REJECTED";
  reviewMemo?: string;
  trusted?: boolean;
  createRequiresReview?: boolean;
  editRequiresReview?: boolean;
  maxActivePosts?: number;
}) {
  return api<EnterpriseApplication>(`/admin/enterprise/applications/${id}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function listAdminEnterpriseJobs(params: { status?: string; keyword?: string } = {}) {
  const search = new URLSearchParams();
  if (params.status) search.set("status", params.status);
  if (params.keyword) search.set("keyword", params.keyword);
  const q = search.toString();
  return api<EnterpriseJob[]>(`/admin/enterprise/jobs${q ? `?${q}` : ""}`, { method: "GET" });
}

export function reviewAdminEnterpriseJob(id: number, payload: { action: "APPROVE" | "REJECT"; reviewMemo?: string }) {
  return api<EnterpriseJob>(`/admin/enterprise/jobs/${id}/review`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}
