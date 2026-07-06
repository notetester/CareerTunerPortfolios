import { AdminDashboardPage } from "./pages/AdminDashboard";
import { AdminOpsDashboardPage } from "./features/dashboard/pages/AdminOpsDashboardPage";
import { AdminPaymentsPage } from "./features/billing/pages/AdminPaymentsPage";
import { AdminPlansPage } from "./features/billing/pages/AdminPlansPage";
import { AdminLogsPage } from "./features/system-logs/pages/AdminLogsPage";
import { AdminAnalyticsPage } from "./features/analytics/pages/AdminAnalyticsPage";
import AdminFitAnalysisPage from "./features/fit-analysis/pages/AdminFitAnalysis";
import { AdminHomePage } from "./features/home/pages/AdminHomePage";
// C 소유 프롬프트 운영 페이지. 컴포넌트·백엔드는 구현돼 있었으나 라우트가 없어 접근 불가 상태였다.
import AdminFitAnalysisPromptsPage from "./features/prompts/fit-analysis/pages/AdminFitAnalysisPrompts";
import AdminAnalyticsPromptsPage from "./features/prompts/analytics/pages/AdminAnalyticsPrompts";
import { AdminApplicationCasesPage } from "./features/application-cases/pages/AdminApplicationCasesPage";
import { AdminBlockedUsersPage, AdminEmailAuditPage, AdminSecurityAuditPage, AdminUsersPage } from "./features/users/pages/AdminUsersPage";
import { AdminProfilesPage } from "./features/profiles/pages/AdminProfilesPage";
import { AdminConsentsPage } from "./features/consents/pages/AdminConsentsPage";
import { AdminCompanyAnalysisPage } from "./features/company-analysis/pages/AdminCompanyAnalysisPage";
import { AdminAiUsagePage } from "./features/job-analysis/pages/AdminAiUsagePage";
import { AdminInterviewsPage } from "./features/interviews/pages/AdminInterviewsPage";
import { AdminInterviewReportsPage } from "./features/interview-reports/pages/AdminInterviewReportsPage";
import { AdminInterviewKnowledgePage } from "./features/interview-knowledge/pages/AdminInterviewKnowledgePage";
import { AdminJobAnalysisPage } from "./features/job-analysis/pages/AdminJobAnalysisPage";
import { AdminPromptsPage } from "./features/prompts/pages/AdminPromptsPage";
import AdminProfilePromptsPage from "./features/prompts/profile/pages/AdminProfilePrompts";
import { AdminAiSettingsPage } from "./features/settings/pages/AdminAiSettingsPage";
import AdminInterviewPromptsPage from "./features/prompts/interview/pages/AdminInterviewPrompts";
import AdminReports from "./features/community/pages/AdminReports";
import AdminGuidelines from "./features/community/pages/AdminGuidelines";
import AdminNotices from "./features/notices/pages/AdminNotices";
import NoticeCompose from "./features/notices/pages/NoticeCompose";
import AdminFaq from "./features/faqs/pages/AdminFaq";
import FaqCompose from "./features/faqs/pages/FaqCompose";
import AdminAiSupport from "./features/ai-support/pages/AdminAiSupport";
import AdminInquiries from "./features/support-tickets/pages/AdminInquiriesAI";
import AdminNotifications from "./features/notifications/pages/AdminNotifications";
import AdminTerms from "./features/terms/pages/AdminTerms";
import { AdminActionLogsPage } from "./features/action-logs/pages/AdminActionLogsPage";
import { AdminPoliciesPage } from "./features/policies/pages/AdminPoliciesPage";
import { AdminSuperAdminPage } from "./features/super-admin/pages/AdminSuperAdminPage";
// W1: 기업 계정 신청 승인/반려 + 채용공고 검토 큐
import { AdminCompanyApplicationsPage } from "./features/company/pages/AdminCompanyApplicationsPage";
import { AdminJobPostingReviewPage } from "./features/company/pages/AdminJobPostingReviewPage";
// W4: 관리자 알림 수신 설정(카테고리 opt-out)
import AdminNotificationPreferences from "./features/notification-preferences/pages/AdminNotificationPreferences";
// W7: 광고 관리(내 구현 채택 — 통계·이미지 업로드·그리드)
import AdminAdsPage from "./features/ads/pages/AdminAdsPage";
// dev: 보안 운영 센터(합체 유지)
import { AdminSecurityOpsPage } from "./features/security-ops/pages/AdminSecurityOpsPage";
import { AdminActivityLogsPage } from "./features/activity-logs/pages/AdminActivityLogsPage";
// 런타임 설정 콘솔(트립투게더 이식): 코드가 실시간 참조하는 key-value 설정 + 변경 이력
import { AdminRuntimeSettingsPage } from "./features/runtime-settings/pages/AdminRuntimeSettingsPage";
import { AdminCorrectionsPage } from "./features/corrections/pages/AdminCorrectionsPage";
import { AdminCreditsPage } from "./features/credits/pages/AdminCreditsPage";
// 이메일 발급 전역 감사(트립투게더 이식): 전체 계정 인증/재설정 토큰 발급 이력 검색
import { AdminEmailAuditLogPage } from "./features/email-audit/pages/AdminEmailAuditLogPage";
// 로그인 위험도 잠금 정책(트립투게더 이식): 브루트포스 자동 잠금 토글 + 임계 편집
import { AdminLoginRiskPolicyPage } from "./features/login-risk/pages/AdminLoginRiskPolicyPage";
// 챗봇 거버넌스(트립투게더 이식): 일일 쿼터 정책 토글 + 대화 세션 목록/삭제
import { AdminChatbotGovernancePage } from "./features/chatbot-governance/pages/AdminChatbotGovernancePage";
// 리워드/레벨 이코노미: 적립 규칙 on/off·레벨 정책·쿠폰·리워드 이력
import { AdminRewardsPage } from "./features/reward/pages/AdminRewardsPage";
// 관리자/직원 등급·급여 관리(최고 관리자 전용): 조직 등급 + 기본급 + Excel 업로드/내보내기
import { AdminStaffGradePage } from "./features/staff-grade/pages/AdminStaffGradePage";

export const adminRoutes = [
  { path: "admin", Component: AdminDashboardPage },
  { path: "admin/home", Component: AdminHomePage },
  { path: "admin/dashboard", Component: AdminOpsDashboardPage },
  // C 분석 통계 전용 화면. 백엔드/api/types는 완비됐으나 전용 페이지·라우트가 없어 연결한다.
  { path: "admin/analytics", Component: AdminAnalyticsPage },
  // C 적합도 운영 화면은 구현돼 있었지만 접근 경로가 없어 완료 기준 충족을 위해 라우트만 연결한다.
  { path: "admin/fit-analysis", Component: AdminFitAnalysisPage },
  // C 프롬프트 운영 확인(적합도/장기 분석). 페이지·백엔드 존재했으나 라우트 누락이라 연결한다.
  { path: "admin/prompts/fit-analysis", Component: AdminFitAnalysisPromptsPage },
  { path: "admin/prompts/analytics", Component: AdminAnalyticsPromptsPage },
  // W1: 기업 신청 승인/반려, 공고 검토 큐
  { path: "admin/company/applications", Component: AdminCompanyApplicationsPage },
  { path: "admin/company/job-postings", Component: AdminJobPostingReviewPage },
  { path: "admin/notification-settings", Component: AdminNotificationPreferences },
  { path: "admin/ads", Component: AdminAdsPage },
  { path: "admin/users", Component: AdminUsersPage },
  { path: "admin/users/blocked", Component: AdminBlockedUsersPage },
  { path: "admin/security", Component: AdminSecurityOpsPage },
  { path: "admin/security/login-risk", Component: AdminLoginRiskPolicyPage },
  { path: "admin/audit/security", Component: AdminSecurityAuditPage },
  { path: "admin/audit/email", Component: AdminEmailAuditPage },
  { path: "admin/audit/activity", Component: AdminActivityLogsPage },
  { path: "admin/audit/email-log", Component: AdminEmailAuditLogPage },
  { path: "admin/profiles", Component: AdminProfilesPage },
  { path: "admin/consents", Component: AdminConsentsPage },
  { path: "admin/super", Component: AdminSuperAdminPage },
  { path: "admin/policies", Component: AdminPoliciesPage },
  { path: "admin/runtime-settings", Component: AdminRuntimeSettingsPage },
  { path: "admin/action-logs", Component: AdminActionLogsPage },
  { path: "admin/payments", Component: AdminPaymentsPage },
  { path: "admin/credits", Component: AdminCreditsPage },
  { path: "admin/rewards", Component: AdminRewardsPage },
  { path: "admin/staff-grades", Component: AdminStaffGradePage },
  { path: "admin/application-cases", Component: AdminApplicationCasesPage },
  { path: "admin/ai-usage", Component: AdminAiUsagePage },
  { path: "admin/chatbot-governance", Component: AdminChatbotGovernancePage },
  { path: "admin/ai-settings", Component: AdminAiSettingsPage },
  { path: "admin/job-analysis", Component: AdminJobAnalysisPage },
  { path: "admin/company-analysis", Component: AdminCompanyAnalysisPage },
  { path: "admin/interviews", Component: AdminInterviewsPage },
  { path: "admin/interview/reports", Component: AdminInterviewReportsPage },
  { path: "admin/corrections", Component: AdminCorrectionsPage },
  { path: "admin/interview/knowledge", Component: AdminInterviewKnowledgePage },
  { path: "admin/community", Component: AdminReports },
  { path: "admin/notices", Component: AdminNotices },
  { path: "admin/notices/new", Component: NoticeCompose },
  { path: "admin/faq", Component: AdminFaq },
  { path: "admin/faq/new", Component: FaqCompose },
  { path: "admin/ai-support", Component: AdminAiSupport },
  { path: "admin/inquiries", Component: AdminInquiries },
  { path: "admin/terms", Component: AdminTerms },
  { path: "admin/terms/guidelines", Component: AdminGuidelines },
  { path: "admin/notifications", Component: AdminNotifications },
  { path: "admin/plans", Component: AdminPlansPage },
  { path: "admin/prompts", Component: AdminPromptsPage },
  { path: "admin/prompts/profile", Component: AdminProfilePromptsPage },
  { path: "admin/prompts/interview", Component: AdminInterviewPromptsPage },
  { path: "admin/logs", Component: AdminLogsPage },
];
