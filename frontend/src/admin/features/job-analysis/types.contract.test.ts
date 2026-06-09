import type { AdminAiUsageLogRow, AdminJobAnalysisRow } from "./types";

const jobAnalysisRow: AdminJobAnalysisRow = {
  id: 11,
  applicationCaseId: 1,
  jobPostingId: 21,
  jobPostingRevision: 2,
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

void jobAnalysisRow;
void failedUsageLog;
