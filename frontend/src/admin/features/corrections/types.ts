export type AdminCorrectionType = "SELF_INTRO" | "INTERVIEW_ANSWER" | "RESUME" | "PORTFOLIO";

export interface AdminCorrectionRow {
  id: number;
  userId: number;
  userEmail: string;
  userName: string;
  applicationCaseId: number | null;
  companyName: string | null;
  jobTitle: string | null;
  correctionType: AdminCorrectionType;
  sourceType: string;
  status: string;
  model: string | null;
  totalTokens: number | null;
  creditUsed: number | null;
  hasMemo: boolean;
  createdAt: string;
}

export interface AdminCorrectionDetail extends Omit<AdminCorrectionRow, "hasMemo"> {
  sourceRefId: number | null;
  originalText: string;
  improvedText: string | null;
  resultJson: string | null;
  aiUsageLogId: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
  adminMemo: string | null;
}

export interface AdminCorrectionFailureRow {
  id: number;
  userId: number;
  userEmail: string;
  userName: string;
  applicationCaseId: number | null;
  companyName: string | null;
  jobTitle: string | null;
  featureType: string;
  model: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  totalTokens: number | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface AdminCorrectionPage {
  items: AdminCorrectionRow[];
  total: number;
  page: number;
  size: number;
}

export interface AdminCorrectionSummary {
  totalRequests: number;
  successCount: number;
  failureCount: number;
  memoCount: number;
  todayCount: number;
}

export interface AdminCorrectionFilters {
  keyword?: string;
  correctionType?: AdminCorrectionType;
  memoState?: "HAS_MEMO" | "NO_MEMO";
  page?: number;
  size?: number;
}

export interface ParsedCorrectionResult {
  summary: string;
  issues: string[];
  changeReasons: string[];
  suggestions: string[];
  raw: string | null;
}
