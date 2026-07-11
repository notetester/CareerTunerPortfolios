export interface AdminSystemPolicyRow {
  policyCode: string;
  displayName: string;
  description: string | null;
  configJson: string;
  scheduleType: string;
  active: boolean;
  lastRunAt: string | null;
  lastRunStatus: string | null;
  lastRunMessage: string | null;
  updatedBy: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminPolicyRunResult {
  policyCode: string;
  status: string;
  message: string;
  runAt: string;
}
