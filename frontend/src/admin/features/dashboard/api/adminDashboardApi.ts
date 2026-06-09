import { api } from "@/app/lib/api";

import type { AdminDashboardOverview } from "../types/adminDashboard";

export function getAdminDashboardOverview() {
  return api<AdminDashboardOverview>("/admin/dashboard/overview");
}
