export interface DashboardUser {
  name: string;
  plan: string;
  credit: number;
}

export interface DashboardStats {
  activeApplications: number;
  newApplicationsThisMonth: number;
  totalInterviews: number;
  interviewsThisWeek: number;
  credit: number;
  creditLimit: number;
  creditsUsedThisMonth: number;
  averageFitScore: number;
}

export interface DashboardFocus {
  headline: string;
  description: string;
  readiness: number | null;
}

export interface DashboardApplication {
  id: number;
  companyName: string;
  jobTitle: string;
  postingDate: string | null;
  status: string;
  favorite: boolean;
  fitScore: number | null;
  interviewCount: number;
  latestInterviewScore: number | null;
  tags: string[];
  updatedAt: string;
  analyzedAt: string | null;
}

export interface DashboardTodo {
  done: boolean;
  task: string;
  time: string;
}

export interface DashboardActivity {
  type: "FIT_ANALYSIS" | "INTERVIEW" | "APPLICATION" | string;
  applicationCaseId: number | null;
  content: string;
  occurredAt: string;
  score: number | null;
}

export interface DashboardSkillGap {
  skill: string;
  count: number;
  total: number;
  percentage: number;
}

export interface DashboardAnalysisRun {
  id: number;
  analysisType: string;
  status: "SUCCESS" | "FALLBACK" | "FAILED" | string;
  model: string | null;
  tokenUsage: number;
  errorMessage: string | null;
  retryable: boolean;
  createdAt: string;
}

export interface DashboardSummary {
  user: DashboardUser;
  stats: DashboardStats;
  focus: DashboardFocus;
  recentApplications: DashboardApplication[];
  todos: DashboardTodo[];
  activities: DashboardActivity[];
  skillGaps: DashboardSkillGap[];
  /** 대시보드 AI 분석 결과 요약(C 담당 AI 18). API 키가 없으면 결정적 mock, 있으면 실제 구조화 분석. */
  aiSummary: string;
  analysisRun: DashboardAnalysisRun;
}
