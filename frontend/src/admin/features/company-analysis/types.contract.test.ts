import type {
  AdminCompanyAnalysisQueryParams,
  AdminCompanyAnalysisRow,
  AdminCompanyAnalysisSummaryResponse,
} from "./types";

const companyAnalysisQueryParams: AdminCompanyAnalysisQueryParams = {
  keyword: "Example",
  sourceType: "WEB",
  industry: "SaaS",
  confirmed: true,
  hasMemo: false,
  checked: true,
  refreshDue: false,
  applicationCaseId: 1,
  userId: 10,
  createdFrom: "2026-06-01",
  createdTo: "2026-06-30",
  sort: "createdAt_desc",
  limit: 30,
  offset: 0,
};

const companyAnalysisRow: AdminCompanyAnalysisRow = {
  id: 12,
  applicationCaseId: 1,
  jobPostingId: 21,
  jobPostingRevision: 2,
  latestJobPostingRevision: 3,
  staleAgainstLatestPosting: true,
  userId: 10,
  userEmail: "user@example.com",
  companyName: "Example Co",
  jobTitle: "Frontend Engineer",
  companySummary: "Company summary",
  recentIssues: null,
  industry: "SaaS",
  competitors: null,
  interviewPoints: null,
  sources: null,
  verifiedFacts: "Verified facts",
  aiInferences: "AI inferences",
  unknowns: "[{\"topic\":\"매출 규모\",\"reason\":\"공고문에 관련 정보가 없다\"}]",
  sourceType: "WEB",
  checkedAt: "2026-06-02T00:00:00Z",
  refreshRecommendedAt: "2026-06-09T00:00:00Z",
  confirmedAt: null,
  adminMemo: null,
  createdAt: "2026-06-02T00:00:00Z",
};

const companyAnalysisSummary: AdminCompanyAnalysisSummaryResponse = {
  totalCount: 8,
  confirmedCount: 3,
  unconfirmedCount: 5,
  refreshDueCount: 4,
  missingSourceCount: 1,
  checkedCount: 6,
  memoCount: 2,
};

void companyAnalysisQueryParams;
void companyAnalysisRow;
void companyAnalysisSummary;
