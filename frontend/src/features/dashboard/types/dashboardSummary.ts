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

export interface DashboardSummary {
  user: DashboardUser;
  stats: DashboardStats;
  focus: DashboardFocus;
  recentApplications: DashboardApplication[];
  todos: DashboardTodo[];
  activities: DashboardActivity[];
  skillGaps: DashboardSkillGap[];
  /** 대시보드 AI 분석 결과 요약(C 담당 AI 18). 현재 백엔드 mock, API 키 주입 시 실 분석으로 전환. */
  aiSummary: string;
}
