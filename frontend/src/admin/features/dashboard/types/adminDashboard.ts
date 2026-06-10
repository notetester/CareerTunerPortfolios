/**
 * 관리자 운영 종합 대시보드 타입(C 담당). 백엔드 /api/admin/dashboard/overview 응답 형태.
 * 분석 통계(adminAnalytics)와 달리 도메인 횡단 운영 현황 카운트를 담는다.
 */
export interface AdminDashboardOverview {
  totalUsers: number;
  activeUsers: number;
  totalApplications: number;
  totalFitAnalyses: number;
  totalInterviewSessions: number;
  aiCallsThisMonth: number;
}
