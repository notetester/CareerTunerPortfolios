export interface AdminAnalyticsStats {
  totalUsers: number;
  activeUsers: number;
  totalApplications: number;
  analyzedApplications: number;
  totalInterviews: number;
  averageFitScore: number;
  creditsUsedThisMonth: number;
}

export interface AdminCount {
  label: string;
  count: number;
}

export interface AdminSkillGap {
  skill: string;
  count: number;
  total: number;
  percentage: number;
}

export interface AdminFitScoreBand {
  label: string;
  count: number;
  percentage: number;
}

export interface AdminRecentAnalysis {
  applicationCaseId: number;
  fitAnalysisId: number;
  userName: string;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  fitScore: number | null;
  analyzedAt: string;
}

export interface AdminDailyUsage {
  date: string;
  tokenUsage: number;
  creditUsed: number;
}

export interface AdminAnalyticsSummary {
  stats: AdminAnalyticsStats;
  planDistribution: AdminCount[];
  applicationStatusDistribution: AdminCount[];
  skillGaps: AdminSkillGap[];
  fitScoreBands: AdminFitScoreBand[];
  recentAnalyses: AdminRecentAnalysis[];
  dailyUsage: AdminDailyUsage[];
}

/** 분석 실패 큐 항목(fit_analysis + career_analysis_run 의 FAILED/FALLBACK 결과). */
export interface AdminAnalysisFailure {
  source: "FIT_ANALYSIS" | "CAREER_TREND" | "DASHBOARD_SUMMARY" | string;
  refId: number;
  userName: string;
  userEmail: string;
  companyName: string | null;
  jobTitle: string | null;
  status: "FAILED" | "FALLBACK" | string;
  errorMessage: string | null;
  model: string | null;
  retryable: boolean;
  createdAt: string;
}

/** 품질 검수 큐 항목(최신 적합도 분석에 대한 결정적 휴리스틱 점검). */
export interface AdminQualityFlag {
  fitAnalysisId: number;
  applicationCaseId: number;
  userName: string;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  fitScore: number | null;
  flagType:
    | "SCORE_GAP_MISMATCH"
    | "LOW_SCORE_NO_GAPS"
    | "EXCESSIVE_CERTS"
    | "EMPTY_STRATEGY"
    | "DEGRADED_RESULT"
    | string;
  severity: "HIGH" | "MEDIUM" | "LOW" | string;
  detail: string;
  analyzedAt: string;
}

export interface AdminCareerAnalysisRun {
  id: number;
  userId: number;
  userName: string;
  userEmail: string;
  analysisType: "CAREER_TREND" | "DASHBOARD_SUMMARY" | string;
  status: "SUCCESS" | "FALLBACK" | "FAILED" | string;
  inputSnapshot: string | null;
  result: string | null;
  model: string | null;
  tokenUsage: number;
  errorMessage: string | null;
  retryable: boolean;
  createdAt: string;
  memoCount: number;
  latestMemoAt: string | null;
}

export type AdminCareerRunMemoType = "GENERAL" | "QUALITY" | "USER_INQUIRY" | "REANALYSIS" | string;

export interface AdminCareerRunMemo {
  id: number;
  careerAnalysisRunId: number;
  adminUserId: number;
  adminName: string;
  adminEmail: string;
  memoType: AdminCareerRunMemoType;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminCareerRunMemoRequest {
  memoType: AdminCareerRunMemoType;
  content: string;
}
