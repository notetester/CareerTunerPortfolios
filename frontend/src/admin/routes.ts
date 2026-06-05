import { AdminDashboardPage } from "./pages/AdminDashboard";

export const adminRoutes = [
  { path: "admin", Component: AdminDashboardPage },
  { path: "admin/users", Component: AdminDashboardPage },
  { path: "admin/payments", Component: AdminDashboardPage },
  { path: "admin/ai-usage", Component: AdminDashboardPage },
  { path: "admin/community", Component: AdminDashboardPage },
  { path: "admin/notices", Component: AdminDashboardPage },
  { path: "admin/plans", Component: AdminDashboardPage },
  { path: "admin/prompts", Component: AdminDashboardPage },
  { path: "admin/logs", Component: AdminDashboardPage },
];
