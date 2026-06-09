import { api } from "@/app/lib/api";

import type { AdminHomeSummary } from "../types/adminHome";

export function getAdminHomeSummary() {
  return api<AdminHomeSummary>("/admin/home/summary");
}
