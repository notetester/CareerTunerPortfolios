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
import { AdminUsersPage } from "./features/users/pages/AdminUsersPage";
import { AdminProfilesPage } from "./features/profiles/pages/AdminProfilesPage";
import { AdminConsentsPage } from "./features/consents/pages/AdminConsentsPage";
import { AdminCompanyAnalysisPage } from "./features/company-analysis/pages/AdminCompanyAnalysisPage";
import { AdminAiUsagePage } from "./features/job-analysis/pages/AdminAiUsagePage";
import { AdminInterviewsPage } from "./features/interviews/pages/AdminInterviewsPage";
import { AdminInterviewKnowledgePage } from "./features/interview-knowledge/pages/AdminInterviewKnowledgePage";
import { AdminJobAnalysisPage } from "./features/job-analysis/pages/AdminJobAnalysisPage";
import { AdminPromptsPage } from "./features/prompts/pages/AdminPromptsPage";
import AdminProfilePromptsPage from "./features/prompts/profile/pages/AdminProfilePrompts";
import AdminInterviewPromptsPage from "./features/prompts/interview/pages/AdminInterviewPrompts";
import AdminReports from "./features/community/pages/AdminReports";
import AdminGuidelines from "./features/community/pages/AdminGuidelines";
import AdminNotices from "./features/notices/pages/AdminNotices";
import NoticeCompose from "./features/notices/pages/NoticeCompose";
import AdminFaq from "./features/faqs/pages/AdminFaq";
import FaqCompose from "./features/faqs/pages/FaqCompose";
import AdminInquiries from "./features/support-tickets/pages/AdminInquiriesAI";
import AdminNotifications from "./features/notifications/pages/AdminNotifications";
import AdminTerms from "./features/terms/pages/AdminTerms";

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
  { path: "admin/users", Component: AdminUsersPage },
  { path: "admin/profiles", Component: AdminProfilesPage },
  { path: "admin/consents", Component: AdminConsentsPage },
  { path: "admin/payments", Component: AdminPaymentsPage },
  { path: "admin/application-cases", Component: AdminApplicationCasesPage },
  { path: "admin/ai-usage", Component: AdminAiUsagePage },
  { path: "admin/job-analysis", Component: AdminJobAnalysisPage },
  { path: "admin/company-analysis", Component: AdminCompanyAnalysisPage },
  { path: "admin/interviews", Component: AdminInterviewsPage },
  { path: "admin/interview/knowledge", Component: AdminInterviewKnowledgePage },
  { path: "admin/community", Component: AdminReports },
  { path: "admin/notices", Component: AdminNotices },
  { path: "admin/notices/new", Component: NoticeCompose },
  { path: "admin/faq", Component: AdminFaq },
  { path: "admin/faq/new", Component: FaqCompose },
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
