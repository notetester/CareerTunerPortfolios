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
  /** 사용자 직접 추가 항목만 존재. 파생 항목은 null. */
  id: number | null;
  /** 파생(자동 계산) 항목만 존재. 사용자 항목은 null. */
  derivedKey: string | null;
  source: "DERIVED" | "USER" | string;
  done: boolean;
  task: string;
  time: string;
}

/** 최근 면접 카드(디자인 분석 §6.4: 점수 변화·핵심 개선점·리포트 보기). 면접 기록이 없으면 null. */
export interface DashboardRecentInterview {
  sessionId: number;
  applicationCaseId: number;
  companyName: string;
  jobTitle: string;
  mode: string;
  totalScore: number | null;
  previousScore: number | null;
  scoreDelta: number | null;
  keyImprovement: string | null;
  occurredAt: string;
}

/** 최근 알림(notification 읽기 전용 참조 — PRODUCT_STRUCTURE 대시보드 항목). */
export interface DashboardNotification {
  id: number;
  type: string;
  title: string;
  message: string | null;
  link: string | null;
  read: boolean;
  createdAt: string;
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

/** 준비도 게이지 구성 요소(0~100). */
export interface DashboardReadinessComponent {
  key: string;
  label: string;
  score: number;
  description: string;
}

/** 전체 취업 준비도 게이지(분석 실행률·평균 적합도·학습 완료율·면접 연습률 가중 평균). */
export interface DashboardReadiness {
  overall: number;
  components: DashboardReadinessComponent[];
}

/** 최근 변화 요약. 재분석 이력이 없으면 averageScoreDelta 가 null. */
export interface DashboardChange {
  reanalyzedApplications: number;
  improvedApplications: number;
  declinedApplications: number;
  averageScoreDelta: number | null;
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
  recentInterview: DashboardRecentInterview | null;
  recentNotifications: DashboardNotification[];
  readiness: DashboardReadiness;
  recentChange: DashboardChange;
  /** 대시보드 AI 분석 결과 요약(C 담당 AI 18). API 키가 없으면 결정적 mock, 있으면 실제 구조화 분석. */
  aiSummary: string;
  analysisRun: DashboardAnalysisRun;
}
