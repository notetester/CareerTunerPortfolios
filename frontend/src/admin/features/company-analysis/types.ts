export interface AdminCompanyAnalysisRow {
  id: number;
  applicationCaseId: number;
  userId: number;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  companySummary: string | null;
  recentIssues: string | null;
  industry: string | null;
  competitors: string | null;
  sources: string | null;
  createdAt: string;
}
