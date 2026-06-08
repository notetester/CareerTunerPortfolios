export interface AdminJobAnalysisRow {
  id: number;
  applicationCaseId: number;
  userId: number;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  employmentType: string | null;
  experienceLevel: string | null;
  requiredSkills: string | null;
  preferredSkills: string | null;
  difficulty: string | null;
  summary: string | null;
  createdAt: string;
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
