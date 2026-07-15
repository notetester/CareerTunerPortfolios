import { createElement, lazy, Suspense, type ComponentType } from "react";
import { PageFallback } from "../app/pages/pageFallback";
import { AdminRouteBoundary } from "./auth/AdminRouteBoundary";
import { adminRoutePolicy, type AdminRoutePath } from "./auth/adminAccess";

function lazyAdminPage(loader: () => Promise<{ default: ComponentType }>): ComponentType {
  const LazyPage = lazy(loader);
  return function LazyAdminRoute() {
    return createElement(Suspense, { fallback: createElement(PageFallback) }, createElement(LazyPage));
  };
}

function adminRoute(path: AdminRoutePath, Component: ComponentType) {
  const adminAccess = adminRoutePolicy(path);
  function GuardedAdminRoute() {
    return createElement(AdminRouteBoundary, {
      policy: adminAccess,
      // render callback 안쪽에서만 lazy route element를 만들어 비인가 요청은 import도 시작하지 않는다.
      render: () => createElement(Component),
    });
  }
  return { path, Component: GuardedAdminRoute, handle: { adminAccess } };
}

const AdminPortalPage = lazyAdminPage(() => import("./features/portal/pages/AdminPortalPage").then((module) => ({ default: module.AdminPortalPage })));
const AdminHomePage = lazyAdminPage(() => import("./features/home/pages/AdminHomePage").then((module) => ({ default: module.AdminHomePage })));
const AdminOpsDashboardPage = lazyAdminPage(() => import("./features/dashboard/pages/AdminOpsDashboardPage").then((module) => ({ default: module.AdminOpsDashboardPage })));
const AdminAnalyticsPage = lazyAdminPage(() => import("./features/analytics/pages/AdminAnalyticsPage").then((module) => ({ default: module.AdminAnalyticsPage })));
const AdminFitAnalysisPage = lazyAdminPage(() => import("./features/fit-analysis/pages/AdminFitAnalysis").then((module) => ({ default: module.default })));
const AdminFitAnalysisPromptsPage = lazyAdminPage(() => import("./features/prompts/fit-analysis/pages/AdminFitAnalysisPrompts").then((module) => ({ default: module.default })));
const AdminAnalyticsPromptsPage = lazyAdminPage(() => import("./features/prompts/analytics/pages/AdminAnalyticsPrompts").then((module) => ({ default: module.default })));
const AdminCompanyApplicationsPage = lazyAdminPage(() => import("./features/company/pages/AdminCompanyApplicationsPage").then((module) => ({ default: module.AdminCompanyApplicationsPage })));
const AdminJobPostingReviewPage = lazyAdminPage(() => import("./features/company/pages/AdminJobPostingReviewPage").then((module) => ({ default: module.AdminJobPostingReviewPage })));
const AdminNotificationPreferences = lazyAdminPage(() => import("./features/notification-preferences/pages/AdminNotificationPreferences").then((module) => ({ default: module.default })));
const AdminAdsPage = lazyAdminPage(() => import("./features/ads/pages/AdminAdsPage").then((module) => ({ default: module.default })));
const AdminUsersPage = lazyAdminPage(() => import("./features/users/pages/AdminUsersPage").then((module) => ({ default: module.AdminUsersPage })));
const AdminBlockedUsersPage = lazyAdminPage(() => import("./features/users/pages/AdminUsersPage").then((module) => ({ default: module.AdminBlockedUsersPage })));
const AdminSecurityOpsPage = lazyAdminPage(() => import("./features/security-ops/pages/AdminSecurityOpsPage").then((module) => ({ default: module.AdminSecurityOpsPage })));
const AdminLoginRiskPolicyPage = lazyAdminPage(() => import("./features/login-risk/pages/AdminLoginRiskPolicyPage").then((module) => ({ default: module.AdminLoginRiskPolicyPage })));
const AdminMfaPolicyPage = lazyAdminPage(() => import("./features/mfa/pages/AdminMfaPolicyPage").then((module) => ({ default: module.AdminMfaPolicyPage })));
const AdminSecurityAuditPage = lazyAdminPage(() => import("./features/users/pages/AdminUsersPage").then((module) => ({ default: module.AdminSecurityAuditPage })));
const AdminActivityLogsPage = lazyAdminPage(() => import("./features/activity-logs/pages/AdminActivityLogsPage").then((module) => ({ default: module.AdminActivityLogsPage })));
const AdminEmailAuditLogPage = lazyAdminPage(() => import("./features/email-audit/pages/AdminEmailAuditLogPage").then((module) => ({ default: module.AdminEmailAuditLogPage })));
const AdminProfilesPage = lazyAdminPage(() => import("./features/profiles/pages/AdminProfilesPage").then((module) => ({ default: module.AdminProfilesPage })));
const AdminConsentsPage = lazyAdminPage(() => import("./features/consents/pages/AdminConsentsPage").then((module) => ({ default: module.AdminConsentsPage })));
const AdminSuperAdminPage = lazyAdminPage(() => import("./features/super-admin/pages/AdminSuperAdminPage").then((module) => ({ default: module.AdminSuperAdminPage })));
const AdminPoliciesPage = lazyAdminPage(() => import("./features/policies/pages/AdminPoliciesPage").then((module) => ({ default: module.AdminPoliciesPage })));
const AdminRuntimeSettingsPage = lazyAdminPage(() => import("./features/runtime-settings/pages/AdminRuntimeSettingsPage").then((module) => ({ default: module.AdminRuntimeSettingsPage })));
const AdminActionLogsPage = lazyAdminPage(() => import("./features/action-logs/pages/AdminActionLogsPage").then((module) => ({ default: module.AdminActionLogsPage })));
const AdminPaymentsPage = lazyAdminPage(() => import("./features/billing/pages/AdminPaymentsPage").then((module) => ({ default: module.AdminPaymentsPage })));
const AdminCreditsPage = lazyAdminPage(() => import("./features/credits/pages/AdminCreditsPage").then((module) => ({ default: module.AdminCreditsPage })));
const AdminRewardsPage = lazyAdminPage(() => import("./features/reward/pages/AdminRewardsPage").then((module) => ({ default: module.AdminRewardsPage })));
const AdminStaffGradePage = lazyAdminPage(() => import("./features/staff-grade/pages/AdminStaffGradePage").then((module) => ({ default: module.AdminStaffGradePage })));
const AdminApplicationCasesPage = lazyAdminPage(() => import("./features/application-cases/pages/AdminApplicationCasesPage").then((module) => ({ default: module.AdminApplicationCasesPage })));
const AdminAiUsagePage = lazyAdminPage(() => import("./features/job-analysis/pages/AdminAiUsagePage").then((module) => ({ default: module.AdminAiUsagePage })));
const AdminChatbotGovernancePage = lazyAdminPage(() => import("./features/chatbot-governance/pages/AdminChatbotGovernancePage").then((module) => ({ default: module.AdminChatbotGovernancePage })));
const AdminAiSettingsPage = lazyAdminPage(() => import("./features/settings/pages/AdminAiSettingsPage").then((module) => ({ default: module.AdminAiSettingsPage })));
const AdminJobAnalysisPage = lazyAdminPage(() => import("./features/job-analysis/pages/AdminJobAnalysisPage").then((module) => ({ default: module.AdminJobAnalysisPage })));
const AdminCompanyAnalysisPage = lazyAdminPage(() => import("./features/company-analysis/pages/AdminCompanyAnalysisPage").then((module) => ({ default: module.AdminCompanyAnalysisPage })));
const AdminInterviewsPage = lazyAdminPage(() => import("./features/interviews/pages/AdminInterviewsPage").then((module) => ({ default: module.AdminInterviewsPage })));
const AdminInterviewReportsPage = lazyAdminPage(() => import("./features/interview-reports/pages/AdminInterviewReportsPage").then((module) => ({ default: module.AdminInterviewReportsPage })));
const AdminCorrectionsPage = lazyAdminPage(() => import("./features/corrections/pages/AdminCorrectionsPage").then((module) => ({ default: module.AdminCorrectionsPage })));
const AdminInterviewKnowledgePage = lazyAdminPage(() => import("./features/interview-knowledge/pages/AdminInterviewKnowledgePage").then((module) => ({ default: module.AdminInterviewKnowledgePage })));
const AdminReports = lazyAdminPage(() => import("./features/community/pages/AdminReports").then((module) => ({ default: module.default })));
const AdminNotices = lazyAdminPage(() => import("./features/notices/pages/AdminNotices").then((module) => ({ default: module.default })));
const NoticeCompose = lazyAdminPage(() => import("./features/notices/pages/NoticeCompose").then((module) => ({ default: module.default })));
const AdminFaq = lazyAdminPage(() => import("./features/faqs/pages/AdminFaq").then((module) => ({ default: module.default })));
const FaqCompose = lazyAdminPage(() => import("./features/faqs/pages/FaqCompose").then((module) => ({ default: module.default })));
const AdminAiSupport = lazyAdminPage(() => import("./features/ai-support/pages/AdminAiSupport").then((module) => ({ default: module.default })));
const AdminInquiries = lazyAdminPage(() => import("./features/support-tickets/pages/AdminInquiriesAI").then((module) => ({ default: module.default })));
const AdminTerms = lazyAdminPage(() => import("./features/terms/pages/AdminTerms").then((module) => ({ default: module.default })));
const AdminGuidelines = lazyAdminPage(() => import("./features/community/pages/AdminGuidelines").then((module) => ({ default: module.default })));
const AdminNotifications = lazyAdminPage(() => import("./features/notifications/pages/AdminNotifications").then((module) => ({ default: module.default })));
const AdminPlansPage = lazyAdminPage(() => import("./features/billing/pages/AdminPlansPage").then((module) => ({ default: module.AdminPlansPage })));
const AdminPromptsPage = lazyAdminPage(() => import("./features/prompts/pages/AdminPromptsPage").then((module) => ({ default: module.AdminPromptsPage })));
const AdminProfilePromptsPage = lazyAdminPage(() => import("./features/prompts/profile/pages/AdminProfilePrompts").then((module) => ({ default: module.default })));
const AdminInterviewPromptsPage = lazyAdminPage(() => import("./features/prompts/interview/pages/AdminInterviewPrompts").then((module) => ({ default: module.default })));
const AdminLogsPage = lazyAdminPage(() => import("./features/system-logs/pages/AdminLogsPage").then((module) => ({ default: module.AdminLogsPage })));

export const adminRoutes = [
  adminRoute("admin", AdminPortalPage),
  adminRoute("admin/home", AdminHomePage),
  adminRoute("admin/dashboard", AdminOpsDashboardPage),
  // C 분석 통계 전용 화면. 백엔드/api/types는 완비됐으나 전용 페이지·라우트가 없어 연결한다.
  adminRoute("admin/analytics", AdminAnalyticsPage),
  // C 적합도 운영 화면은 구현돼 있었지만 접근 경로가 없어 완료 기준 충족을 위해 라우트만 연결한다.
  adminRoute("admin/fit-analysis", AdminFitAnalysisPage),
  // C 프롬프트 운영 확인(적합도/장기 분석). 페이지·백엔드 존재했으나 라우트 누락이라 연결한다.
  adminRoute("admin/prompts/fit-analysis", AdminFitAnalysisPromptsPage),
  adminRoute("admin/prompts/analytics", AdminAnalyticsPromptsPage),
  // W1: 기업 신청 승인/반려, 공고 검토 큐
  adminRoute("admin/company/applications", AdminCompanyApplicationsPage),
  adminRoute("admin/company/job-postings", AdminJobPostingReviewPage),
  adminRoute("admin/notification-settings", AdminNotificationPreferences),
  adminRoute("admin/ads", AdminAdsPage),
  adminRoute("admin/users", AdminUsersPage),
  adminRoute("admin/users/blocked", AdminBlockedUsersPage),
  adminRoute("admin/security", AdminSecurityOpsPage),
  adminRoute("admin/security/login-risk", AdminLoginRiskPolicyPage),
  adminRoute("admin/security/mfa-policy", AdminMfaPolicyPage),
  adminRoute("admin/audit/security", AdminSecurityAuditPage),
  adminRoute("admin/audit/email", AdminEmailAuditLogPage),
  adminRoute("admin/audit/activity", AdminActivityLogsPage),
  adminRoute("admin/audit/email-log", AdminEmailAuditLogPage),
  adminRoute("admin/profiles", AdminProfilesPage),
  adminRoute("admin/consents", AdminConsentsPage),
  adminRoute("admin/super", AdminSuperAdminPage),
  adminRoute("admin/policies", AdminPoliciesPage),
  adminRoute("admin/runtime-settings", AdminRuntimeSettingsPage),
  adminRoute("admin/action-logs", AdminActionLogsPage),
  adminRoute("admin/payments", AdminPaymentsPage),
  adminRoute("admin/credits", AdminCreditsPage),
  adminRoute("admin/rewards", AdminRewardsPage),
  adminRoute("admin/staff-grades", AdminStaffGradePage),
  adminRoute("admin/application-cases", AdminApplicationCasesPage),
  adminRoute("admin/ai-usage", AdminAiUsagePage),
  adminRoute("admin/chatbot-governance", AdminChatbotGovernancePage),
  adminRoute("admin/ai-settings", AdminAiSettingsPage),
  adminRoute("admin/job-analysis", AdminJobAnalysisPage),
  adminRoute("admin/company-analysis", AdminCompanyAnalysisPage),
  adminRoute("admin/interviews", AdminInterviewsPage),
  adminRoute("admin/interview/reports", AdminInterviewReportsPage),
  adminRoute("admin/corrections", AdminCorrectionsPage),
  adminRoute("admin/interview/knowledge", AdminInterviewKnowledgePage),
  adminRoute("admin/community", AdminReports),
  adminRoute("admin/notices", AdminNotices),
  adminRoute("admin/notices/new", NoticeCompose),
  adminRoute("admin/faq", AdminFaq),
  adminRoute("admin/faq/new", FaqCompose),
  adminRoute("admin/ai-support", AdminAiSupport),
  adminRoute("admin/inquiries", AdminInquiries),
  adminRoute("admin/terms", AdminTerms),
  adminRoute("admin/terms/guidelines", AdminGuidelines),
  adminRoute("admin/notifications", AdminNotifications),
  adminRoute("admin/plans", AdminPlansPage),
  adminRoute("admin/prompts", AdminPromptsPage),
  adminRoute("admin/prompts/profile", AdminProfilePromptsPage),
  adminRoute("admin/prompts/interview", AdminInterviewPromptsPage),
  adminRoute("admin/logs", AdminLogsPage),
];
