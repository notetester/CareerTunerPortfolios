export interface SecurityOpsSummary {
  activeBlockRules: number;
  pendingWafEvents: number;
  openReviews: number;
  openAppeals: number;
  enabledProviders: number;
}

export interface SecurityBlockRule {
  id: number;
  ruleType: string;
  ruleValue: string;
  scope: string;
  actionType: string;
  category: string;
  reason: string | null;
  memo: string | null;
  active: boolean;
  wafSyncEnabled: boolean;
  wafSyncStatus: string;
  wafRuleId: string | null;
  lastSyncedAt: string | null;
  expiresAt: string | null;
  createdBy: number | null;
  updatedBy: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface SecurityProviderConfig {
  id: number;
  providerCode: string;
  displayName: string;
  providerType: string;
  mode: string;
  enabled: boolean;
  endpointUrl: string | null;
  configJson: string;
  healthStatus: string;
  lastCheckedAt: string | null;
  updatedBy: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface SecurityProviderHealthHistory {
  id: number;
  providerConfigId: number;
  providerCode: string;
  providerType: string;
  checkSource: string;
  statusBefore: string | null;
  statusAfter: string;
  detailMessage: string | null;
  actorUserId: number | null;
  actorEmail: string | null;
  checkedAt: string;
}

export interface SecurityReview {
  id: number;
  reviewType: string;
  subjectType: string;
  subjectValue: string;
  riskScore: number;
  riskLevel: string;
  status: string;
  decisionAction: string | null;
  reason: string | null;
  evidenceJson: string;
  createdBy: number | null;
  assignedTo: number | null;
  decidedBy: number | null;
  decidedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SecurityAppealPolicy {
  id: number;
  policyCode: string;
  displayName: string;
  enabled: boolean;
  allowMultipleOpen: boolean;
  maxOpenPerSubject: number;
  submitterDailyLimit: number;
  tokenTtlHours: number;
  configJson: string;
  updatedBy: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface SecurityAppeal {
  id: number;
  publicRequestId: string;
  subjectType: string;
  subjectValue: string;
  blockRuleId: number | null;
  submitterEmail: string;
  status: string;
  reason: string | null;
  decisionReason: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WafSyncEvent {
  id: number;
  blockRuleId: number | null;
  providerCode: string;
  operationType: string;
  status: string;
  requestPayloadJson: string | null;
  responsePayloadJson: string | null;
  errorMessage: string | null;
  requestedBy: number | null;
  requestedAt: string;
  processedAt: string | null;
}
