import type { AdminAiUsageLogRow } from "../job-analysis/types";
import type { CompanyAnalysis, JobAnalysis } from "@/features/applications/types/analysis";
import type { JobPosting } from "@/features/applications/types/jobPosting";
import type { ApplicationSourceType, ApplicationStatus } from "@/features/applications/types/applicationCase";

export interface AdminApplicationCaseRow {
  id: number;
  userId: number;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  postingDate: string | null;
  deadlineDate: string | null;
  sourceType: ApplicationSourceType;
  status: ApplicationStatus;
  favorite: boolean;
  archivedAt: string | null;
  deletedAt: string | null;
  createdAt: string;
  updatedAt: string;
  latestPostingRevision: number | null;
  latestJobAnalysisAt: string | null;
  latestCompanyAnalysisAt: string | null;
}

export interface AdminApplicationJobAnalysis extends JobAnalysis {
  evidence: string | null;
  ambiguousConditions: string | null;
}

export interface AdminApplicationCompanyAnalysis extends CompanyAnalysis {
  verifiedFacts: string | null;
  aiInferences: string | null;
  sourceType: string | null;
  checkedAt: string | null;
  refreshRecommendedAt: string | null;
}

export interface AdminApplicationCaseDetail {
  applicationCase: AdminApplicationCaseRow;
  jobPostings: JobPosting[];
  jobAnalyses: AdminApplicationJobAnalysis[];
  companyAnalyses: AdminApplicationCompanyAnalysis[];
  usageLogs: AdminAiUsageLogRow[];
}
