export interface AdminJobAnalysisQueryParams {
  keyword?: string | null;
  difficulty?: string | null;
  confirmed?: boolean | null;
  hasMemo?: boolean | null;
  applicationCaseId?: number | null;
  userId?: number | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  sort?: string | null;
  limit?: number | null;
  offset?: number | null;
}

export interface AdminJobAnalysisSummaryResponse {
  totalCount: number;
  confirmedCount: number;
  unconfirmedCount: number;
  easyCount: number;
  mediumCount: number;
  hardCount: number;
  unknownDifficultyCount: number;
  memoCount: number;
}

export interface AdminJobAnalysisRow {
  id: number;
  applicationCaseId: number;
  jobPostingId: number | null;
  jobPostingRevision: number | null;
  latestJobPostingRevision: number | null;
  staleAgainstLatestPosting: boolean;
  userId: number;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  employmentType: string | null;
  experienceLevel: string | null;
  requiredSkills: string | null;
  preferredSkills: string | null;
  duties: string | null;
  qualifications: string | null;
  difficulty: string | null;
  summary: string | null;
  evidence: string | null;
  ambiguousConditions: string | null;
  confirmedAt: string | null;
  adminMemo: string | null;
  createdAt: string;
}

export type AdminBUsageFeatureType = "JOB_POSTING_OCR" | "JOB_ANALYSIS" | "COMPANY_RESEARCH";

export type AdminAiUsageStatus = "SUCCESS" | "FAILED" | "FALLBACK";

export interface AdminBUsageLogQueryParams {
  featureType?: AdminBUsageFeatureType | string | null;
  status?: AdminAiUsageStatus | string | null;
  keyword?: string | null;
  applicationCaseId?: number | null;
  userId?: number | null;
  model?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  sort?: string | null;
  limit?: number | null;
  offset?: number | null;
}

export interface AdminBUsageSummaryResponse {
  totalCount: number;
  successCount: number;
  failedCount: number;
  tokenUsage: number;
  creditUsed: number;
  jobAnalysisCount: number;
  companyResearchCount: number;
  jobPostingOcrCount: number;
}

export interface AdminAiUsageLogRow {
  id: number;
  userId: number;
  userEmail: string;
  applicationCaseId: number | null;
  companyName: string | null;
  jobTitle: string | null;
  featureType: string;
  status: string;
  model: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  tokenUsage: number | null;
  creditUsed: number;
  errorMessage: string | null;
  createdAt: string;
}
