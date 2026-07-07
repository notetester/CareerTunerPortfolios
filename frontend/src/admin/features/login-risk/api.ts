import { api } from "@/app/lib/api";

export interface LoginRiskPolicy {
  id: number;
  enabled: boolean;
  maxFailedCount: number;
  lockMinutes: number;
  updatedBy: number | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export function getLoginRiskPolicy(): Promise<LoginRiskPolicy> {
  return api<LoginRiskPolicy>("/admin/security/login-risk-policy");
}

export function updateLoginRiskPolicy(payload: {
  enabled?: boolean;
  maxFailedCount?: number;
  lockMinutes?: number;
  reason?: string;
}): Promise<LoginRiskPolicy> {
  return api<LoginRiskPolicy>("/admin/security/login-risk-policy", {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}
