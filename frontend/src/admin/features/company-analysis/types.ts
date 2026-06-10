export interface AdminCompanyAnalysisRow {
  id: number;
  applicationCaseId: number;
  jobPostingId: number | null;
  jobPostingRevision: number | null;
  userId: number;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  companySummary: string | null;
  recentIssues: string | null;
  industry: string | null;
  competitors: string | null;
  interviewPoints: string | null;
  sources: string | null;
  verifiedFacts: string | null;
  aiInferences: string | null;
  sourceType: string | null;
  checkedAt: string | null;
  refreshRecommendedAt: string | null;
  confirmedAt: string | null;
  adminMemo: string | null;
  createdAt: string;
}

export interface AdminCompanyAnalysisMetadataUpdateRequest {
  sourceType: string | null;
  checkedAt: string | null;
  refreshRecommendedAt: string | null;
}
