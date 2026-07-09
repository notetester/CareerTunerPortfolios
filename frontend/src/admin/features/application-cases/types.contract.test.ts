import type {
  AdminApplicationCaseDetail,
  AdminApplicationCaseQueryParams,
  AdminApplicationCaseRow,
  AdminApplicationCaseSummaryResponse,
} from "./types";

const applicationCaseQueryParams: AdminApplicationCaseQueryParams = {
  keyword: "Frontend",
  status: "READY",
  includeArchived: false,
  includeDeleted: false,
  sourceType: "TEXT",
  favorite: true,
  createdFrom: "2026-06-01",
  createdTo: "2026-06-30",
  deadlineFrom: "2026-06-15",
  deadlineTo: "2026-07-15",
  analysisState: "MISSING_ANY",
  sort: "updatedAt_desc",
  limit: 20,
  offset: 0,
};

const blankApplicationCaseQueryParams: AdminApplicationCaseQueryParams = {
  keyword: "   ",
  status: "",
  limit: undefined,
};

const applicationCaseRow: AdminApplicationCaseRow = {
  id: 1,
  userId: 10,
  userEmail: "user@example.com",
  companyName: "Example Co",
  jobTitle: "Frontend Engineer",
  postingDate: "2026-06-01",
  deadlineDate: "2026-06-30",
  sourceType: "TEXT",
  status: "READY",
  favorite: false,
  archivedAt: null,
  deletedAt: null,
  createdAt: "2026-06-01T00:00:00Z",
  updatedAt: "2026-06-02T00:00:00Z",
  latestPostingRevision: 2,
  latestJobAnalysisAt: "2026-06-02T00:00:00Z",
  latestCompanyAnalysisAt: "2026-06-02T00:00:00Z",
};

const applicationCaseDetail: AdminApplicationCaseDetail = {
  applicationCase: applicationCaseRow,
  jobPostings: [],
  jobAnalyses: [
    {
      id: 11,
      applicationCaseId: 1,
      jobPostingId: 21,
      jobPostingRevision: 2,
      employmentType: "FULL_TIME",
      experienceLevel: "MID",
      requiredSkills: "[\"React\"]",
      preferredSkills: null,
      duties: null,
      qualifications: null,
      difficulty: "NORMAL",
      summary: "Summary",
      evidence: "Evidence text",
      ambiguousConditions: "Ambiguous conditions",
      confirmedAt: null,
      adminMemo: null,
      createdAt: "2026-06-02T00:00:00Z",
    },
  ],
  companyAnalyses: [
    {
      id: 12,
      applicationCaseId: 1,
      jobPostingId: 21,
      jobPostingRevision: 2,
      companySummary: "Company summary",
      recentIssues: null,
      industry: "SaaS",
      competitors: null,
      interviewPoints: null,
      sources: null,
      verifiedFacts: "Verified facts",
      aiInferences: "AI inferences",
      unknowns: null,
      sourceType: "WEB",
      checkedAt: "2026-06-02T00:00:00Z",
      refreshRecommendedAt: "2026-06-09T00:00:00Z",
      confirmedAt: null,
      adminMemo: null,
      createdAt: "2026-06-02T00:00:00Z",
    },
  ],
  usageLogs: [],
};

const applicationCaseSummary: AdminApplicationCaseSummaryResponse = {
  totalCount: 10,
  draftCount: 1,
  analyzingCount: 2,
  readyCount: 4,
  appliedCount: 2,
  closedCount: 1,
  missingJobAnalysisCount: 3,
  missingCompanyAnalysisCount: 4,
  missingAnyAnalysisCount: 5,
  completeAnalysisCount: 5,
  failedUsageCount: 2,
};

void applicationCaseQueryParams;
void blankApplicationCaseQueryParams;
void applicationCaseDetail;
void applicationCaseSummary;
