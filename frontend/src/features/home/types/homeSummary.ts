import type {
  DashboardActivity,
  DashboardApplication,
  DashboardFocus,
  DashboardTodo,
  DashboardUser,
} from "@/features/dashboard/types/dashboardSummary";

/**
 * 로그인 홈 요약(C 담당). 백엔드 /api/home/summary 응답 형태.
 * 대시보드 집계를 재사용하므로 dashboard 타입을 그대로 참조한다(동일 담당 C).
 */
export interface HomeSummary {
  user: DashboardUser;
  focus: DashboardFocus;
  /** 대시보드 AI 분석 요약(C 담당 AI 18). 현재 백엔드 mock. */
  aiSummary: string;
  recentApplications: DashboardApplication[];
  nextActions: DashboardTodo[];
  recentActivities: DashboardActivity[];
}
