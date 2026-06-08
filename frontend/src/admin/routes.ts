import { AdminDashboardPage } from "./pages/AdminDashboard";
import AdminReports from "./features/community/pages/AdminReports";
import AdminNotices from "./features/notices/pages/AdminNotices";
import AdminFaq from "./features/faqs/pages/AdminFaq";
import AdminInquiries from "./features/support-tickets/pages/AdminInquiries";

export const adminRoutes = [
  { path: "admin", Component: AdminDashboardPage },
  { path: "admin/users", Component: AdminDashboardPage },
  { path: "admin/payments", Component: AdminDashboardPage },
  { path: "admin/ai-usage", Component: AdminDashboardPage },
  { path: "admin/community", Component: AdminReports },
  { path: "admin/notices", Component: AdminNotices },
  { path: "admin/faq", Component: AdminFaq },
  { path: "admin/inquiries", Component: AdminInquiries },
  { path: "admin/plans", Component: AdminDashboardPage },
  { path: "admin/prompts", Component: AdminDashboardPage },
  { path: "admin/logs", Component: AdminDashboardPage },
];
