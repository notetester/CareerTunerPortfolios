import { AdminDashboardPage } from "./pages/AdminDashboard";
import { AdminCompanyAnalysisPage } from "./features/company-analysis/pages/AdminCompanyAnalysisPage";
import { AdminAiUsagePage } from "./features/job-analysis/pages/AdminAiUsagePage";
import { AdminJobAnalysisPage } from "./features/job-analysis/pages/AdminJobAnalysisPage";

export const adminRoutes = [
  { path: "admin", Component: AdminDashboardPage },
  { path: "admin/users", Component: AdminDashboardPage },
  { path: "admin/payments", Component: AdminDashboardPage },
  { path: "admin/ai-usage", Component: AdminAiUsagePage },
  { path: "admin/job-analysis", Component: AdminJobAnalysisPage },
  { path: "admin/company-analysis", Component: AdminCompanyAnalysisPage },
  { path: "admin/community", Component: AdminDashboardPage },
  { path: "admin/notices", Component: AdminDashboardPage },
  { path: "admin/plans", Component: AdminDashboardPage },
  { path: "admin/prompts", Component: AdminDashboardPage },
  { path: "admin/logs", Component: AdminDashboardPage },
];
