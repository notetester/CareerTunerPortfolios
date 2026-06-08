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
