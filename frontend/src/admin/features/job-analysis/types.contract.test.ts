import type {
  AdminAiUsageLogRow,
  AdminBUsageLogQueryParams,
  AdminBUsageSummaryResponse,
  AdminJobAnalysisQueryParams,
  AdminJobAnalysisRow,
  AdminJobAnalysisSummaryResponse,
} from "./types";

const jobAnalysisQueryParams: AdminJobAnalysisQueryParams = {
  keyword: "Frontend",
  difficulty: "NORMAL",
  confirmed: false,
  hasMemo: true,
  applicationCaseId: 1,
  userId: 10,
  createdFrom: "2026-06-01",
  createdTo: "2026-06-30",
  sort: "createdAt_desc",
  limit: 30,
  offset: 0,
};

const jobAnalysisRow: AdminJobAnalysisRow = {
  id: 11,
  applicationCaseId: 1,
  jobPostingId: 21,
  jobPostingRevision: 2,
  latestJobPostingRevision: 3,
  staleAgainstLatestPosting: true,
  userId: 10,
  userEmail: "user@example.com",
  companyName: "Example Co",
  jobTitle: "Frontend Engineer",
  employmentType: "FULL_TIME",
  experienceLevel: "MID",
  requiredSkills: "[\"React\"]",
  preferredSkills: null,
  duties: "Build UI",
  qualifications: "TypeScript",
  difficulty: "NORMAL",
  summary: "Summary",
  evidence: "Evidence text",
  ambiguousConditions: "Ambiguous conditions",
  confirmedAt: null,
  adminMemo: null,
  createdAt: "2026-06-02T00:00:00Z",
};

const jobAnalysisSummary: AdminJobAnalysisSummaryResponse = {
  totalCount: 12,
  confirmedCount: 5,
  unconfirmedCount: 7,
  easyCount: 2,
  mediumCount: 6,
  hardCount: 3,
  unknownDifficultyCount: 1,
  memoCount: 3,
};

const usageQueryParams: AdminBUsageLogQueryParams = {
  featureType: "JOB_POSTING_METADATA",
  status: "FAILED",
  keyword: "Example",
  applicationCaseId: 1,
  userId: 10,
  model: "gpt-example",
  createdFrom: "2026-06-01",
  createdTo: "2026-06-30",
  sort: "createdAt_desc",
  limit: 20,
  offset: 0,
};

const blankUsageQueryParams: AdminBUsageLogQueryParams = {
  featureType: "",
  status: "   ",
  limit: null,
};

const failedUsageLog: AdminAiUsageLogRow = {
  id: 31,
  userId: 10,
  userEmail: "user@example.com",
  applicationCaseId: 1,
  companyName: "Example Co",
  jobTitle: "Frontend Engineer",
  featureType: "JOB_ANALYSIS",
  status: "FAILED",
  model: "gpt-example",
  inputTokens: null,
  outputTokens: null,
  tokenUsage: null,
  creditUsed: 0,
  errorMessage: "Model request failed",
  createdAt: "2026-06-02T00:00:00Z",
};

const usageSummary: AdminBUsageSummaryResponse = {
  totalCount: 25,
  successCount: 20,
  failedCount: 5,
  tokenUsage: 15000,
  creditUsed: 25,
  jobAnalysisCount: 10,
  companyResearchCount: 8,
  jobPostingOcrCount: 7,
  jobPostingMetadataCount: 4,
};

void jobAnalysisQueryParams;
void jobAnalysisRow;
void jobAnalysisSummary;
void usageQueryParams;
void blankUsageQueryParams;
void failedUsageLog;
void usageSummary;
