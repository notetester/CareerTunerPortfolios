import { AdminDashboardPage } from "./pages/AdminDashboard";
import { AdminOpsDashboardPage } from "./features/dashboard/pages/AdminOpsDashboardPage";
import AdminFitAnalysisPage from "./features/fit-analysis/pages/AdminFitAnalysis";
import { AdminHomePage } from "./features/home/pages/AdminHomePage";
import { AdminApplicationCasesPage } from "./features/application-cases/pages/AdminApplicationCasesPage";
import { AdminCompanyAnalysisPage } from "./features/company-analysis/pages/AdminCompanyAnalysisPage";
import { AdminAiUsagePage } from "./features/job-analysis/pages/AdminAiUsagePage";
import { AdminInterviewsPage } from "./features/interviews/pages/AdminInterviewsPage";
import { AdminJobAnalysisPage } from "./features/job-analysis/pages/AdminJobAnalysisPage";
import { AdminPromptsPage } from "./features/prompts/pages/AdminPromptsPage";
import AdminReports from "./features/community/pages/AdminReports";
import AdminNotices from "./features/notices/pages/AdminNotices";
import AdminFaq from "./features/faqs/pages/AdminFaq";
import AdminInquiries from "./features/support-tickets/pages/AdminInquiries";

export const adminRoutes = [
  { path: "admin", Component: AdminDashboardPage },
  { path: "admin/home", Component: AdminHomePage },
  { path: "admin/dashboard", Component: AdminOpsDashboardPage },
  // C 적합도 운영 화면은 구현돼 있었지만 접근 경로가 없어 완료 기준 충족을 위해 라우트만 연결한다.
  { path: "admin/fit-analysis", Component: AdminFitAnalysisPage },
  { path: "admin/users", Component: AdminDashboardPage },
  { path: "admin/payments", Component: AdminDashboardPage },
  { path: "admin/application-cases", Component: AdminApplicationCasesPage },
  { path: "admin/ai-usage", Component: AdminAiUsagePage },
  { path: "admin/job-analysis", Component: AdminJobAnalysisPage },
  { path: "admin/company-analysis", Component: AdminCompanyAnalysisPage },
  { path: "admin/interviews", Component: AdminInterviewsPage },
  { path: "admin/community", Component: AdminReports },
  { path: "admin/notices", Component: AdminNotices },
  { path: "admin/faq", Component: AdminFaq },
  { path: "admin/inquiries", Component: AdminInquiries },
  { path: "admin/plans", Component: AdminDashboardPage },
  { path: "admin/prompts", Component: AdminPromptsPage },
  { path: "admin/logs", Component: AdminDashboardPage },
];
