import { api } from "@/app/lib/api";
import type { AdminPolicyRunResult, AdminSystemPolicyRow } from "./types";

export function getAdminPolicies(): Promise<AdminSystemPolicyRow[]> {
  return api<AdminSystemPolicyRow[]>("/admin/policies", { method: "GET" });
}

export function updateAdminPolicy(
  policyCode: string,
  payload: { configJson: string; scheduleType: string; active: boolean; reason?: string },
): Promise<AdminSystemPolicyRow> {
  return api<AdminSystemPolicyRow>(`/admin/policies/${policyCode}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function runAdminPolicy(policyCode: string, reason?: string): Promise<AdminPolicyRunResult> {
  return api<AdminPolicyRunResult>(`/admin/policies/${policyCode}/run`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}
